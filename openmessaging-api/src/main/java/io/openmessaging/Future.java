/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.openmessaging;

/**
 * <p>
 * A {@code Future} represents the result of an asynchronous computation.  Methods are provided to check if the
 * computation is complete, to wait for its completion, and to retrieve the result of the computation.  The result can
 * only be retrieved using method {@code get} when the computation has completed, blocking if necessary until it is
 * ready. Additional methods are provided to determine if the task completed normally or was cancelled.
 * </p>
 * <p>
 * The reason for adding this future is mainly to satisfy the old version of jdk, such as jdk 1.6.
 * </p>
 *
 * @version OMS 1.0.0
 * @since OMS 1.0.0
 */
public interface Future<V> {
    /**
     * Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, has already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when {@code cancel} is called,
     * this task should never run.  If the task has already started,
     * then the {@code mayInterruptIfRunning} parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.
     *
     * <p>After this method returns, subsequent calls to {@link #isDone} will
     * always return {@code true}.  Subsequent calls to {@link #isCancelled}
     * will always return {@code true} if this method returned {@code true}.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed normally;
     * {@code true} otherwise
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Returns {@code true} if this task was cancelled before it completed normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method
     * will return {@code true}.
     *
     * @return {@code true} if this task completed
     */
    boolean isDone();

    /**
     * Waits if necessary for the computation to complete, and then retrieves its result.
     *
     * @return the computed result
     */
    V get();

    /**
     * Waits if necessary for at most the given time for the computation to complete, and then retrieves its result, if
     * available.
     *
     * @param timeout the maximum time to wait
     * @return the computed result <p> if the computation was cancelled
     */
    V get(long timeout);

    /**
     * Adds the specified listener to this future. The specified listener is notified when this future is done. If this
     * future is already completed, the specified listener will be notified immediately.
     *
     * @param listener FutureListener
     */
    void addListener(FutureListener<V> listener);

    /**
     * Returns the cause of the failed future
     *
     * @return the cause of the failure. {@code null} if succeeded or this future is not completed yet.
     */
    Throwable getThrowable();
}
