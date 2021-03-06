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
package co.paralleluniverse.common.collection;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a concurrent blocking multiset −− bag 
 * Transforms existing blocking multisets into multilane forms Exposes take() and put() accessor methods. 
 * Lanes: Array of underlying blocking concurrent collections like: ArrayBlockingQueue, LinkedBlockingQueue, LinkedTransferQueue, SynchronousQueue etc
 *
 * @see http://blogs.oracle.com/dave/resource/spaa11-multiset.pdf
 * @author Dave Dice dave.dice@oracle.com
 * @author Oleksandr Otenko oleksandr.otenko@oracle.com
 */
public class MultiLane<T> {
    // putCursor and takeCursor are write and read "cursors" that chase each other.
    // These are the only central read−write variables in our algorithm.
    // Invariant: the generated indices must follow the same trajectory
    // The stream of indexes generated by putCursor and takeCursor does _not_ need to be strictly cyclic, and in fact will not be when the putCursor
    // and takeCursor overﬂow and change sign. That’s benign.
    // Progress property : obstruction within a lane impacts only that lane.
    // Invariant: if there are any "written" lanes in the MultiLane collection then the lane identiﬁed by takeCursor is written. 
    // "Written" means that the sub−collection at that speciﬁed lane has at least one available element, or that some arriving put() has advanced putCursor and is poised to put() into that lane. 
    // take() may thus pair−up promptly if put messages are available. Complementary invariant follow the same trajectory.
    // The general approach is similar to that of a ring buffer, except that
    // there are no locks at the top level but only atomically updated cursors, and
    // the ring elements are themselves blocking concurrent collections
    private final LinkedBlockingQueue<T>[] lanes;
    private final AtomicInteger putCursor = new AtomicInteger();
    private final AtomicInteger takeCursor = new AtomicInteger();

    public MultiLane(int width) {
        if (width <= 0)
            throw new IllegalArgumentException("width must be positive but is " + width);
        if ((width & (width - 1)) != 0) // For brevity of explication require power−of−two width value that allows efﬁcient indexing of the form : lanes[i & (lanes.length−1)]
            throw new IllegalArgumentException("width must be a power of 2 but is " + width);
        
        lanes = (LinkedBlockingQueue<T>[]) new LinkedBlockingQueue[width];
        for (int i = 0; i < width; i++)
            lanes[i] = new LinkedBlockingQueue<T>();
    }

    public void put(T v) throws InterruptedException {
        final int curs = putCursor.getAndIncrement();
        lanes[curs & (lanes.length - 1)].put(v); // put() is blocking
    }

    public T take() throws InterruptedException {
        final int curs = takeCursor.getAndIncrement(); 
        return lanes[curs & (lanes.length - 1)].take(); // take() is blocking
    }
}