/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
 */
package co.paralleluniverse.galaxy.netty;

import java.util.concurrent.Executor;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;

/**
 *
 * @author pron
 */
public class TcpMessagePipelineFactory implements ChannelPipelineFactory {

    private final Logger logger;
    private final DefaultChannelGroup channelGroup;
    private final ChannelMessageNodeResolver nodeResolver;
    private final int lengthFieldSize;
    private final Executor executor;

    public TcpMessagePipelineFactory(Logger logger, DefaultChannelGroup channelGroup, Executor executor) {
        this.logger = logger;
        this.channelGroup = channelGroup;
        this.nodeResolver = new ChannelAttachedNodeResolver();
        this.lengthFieldSize = 4;
        this.executor = executor;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder((int) ((1L << (lengthFieldSize * 8)) - 1) & (-1 >>> 1), 0, lengthFieldSize, 0, lengthFieldSize));
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(lengthFieldSize, false));
        if (executor != null)
            pipeline.addLast("executor", new ExecutionHandler(executor));
        pipeline.addLast("logging", new LoggingHandler(logger));
        // a node resolver must be added before the mesage codec
        pipeline.addLast("messageCodec", new MessageCodec());
        pipeline.addLast("nodeResolver", nodeResolver);
        pipeline.addLast("common", new SimpleChannelUpstreamHandler() {

            @Override
            public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                if (channelGroup != null)
                    channelGroup.add(e.getChannel());
                super.channelConnected(ctx, e);
            }

            @Override
            public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                if (channelGroup != null)
                    channelGroup.remove(e.getChannel());
                super.channelDisconnected(ctx, e);
                e.getChannel().close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
                e.getChannel().close();
                super.exceptionCaught(ctx, e);
            }

        });
        return pipeline;
    }

}
