package de.lwerner.threads.threadsafequeue;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe queue implementation with pre-defined length and to indexes (read and write).
 *
 * @param <T> Type of queue elements
 *
 * @author Lukas Werner
 * @author Toni Pohl
 */
public class NonBlockingQueue<T> {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final int size;
    private final ArrayList<QueueField<T>> fields;

    private int readIndex;
    private int writeIndex;

    private Lock enqueueLock = new ReentrantLock(); // ReentrantLock because the lock-holder can enter other methods locked with the same lock
    private Lock dequeueLock = new ReentrantLock();

    private Condition enqueueCondition = enqueueLock.newCondition();
    private Condition dequeueCondition = dequeueLock.newCondition();

    /**
     * Sets the size of the queue.
     *
     * @param size the size (number of elements the queue can contain simultaneously)
     */
    public NonBlockingQueue(int size) {
        LOGGER.info(String.format("Creating Queue with size %d.", size));
        this.size = size;
        fields = new ArrayList<>(size);
        while (size-- > 0) {
            fields.add(QueueField.EMPTY);
        }
    }

    /**
     * Enqueues a new element to the current read index
     *
     * @param element element the element to enqueue
     * @param abortOnWait let the method return if wait would be called
     */
    public void enqueue(T element, boolean abortOnWait) {
        enqueueLock.lock();
        try {
            while (!writable()) {
                enqueueCondition.await();
            }

            LOGGER.info(String.format("Enqueuing element %s at index %d.", element.toString(), writeIndex));
            fields.set(writeIndex, new QueueField<>(element));
            writeIndex = (writeIndex + 1) % size; // Thread-safe because only here it'll be updated

            dequeueLock.lock();
            dequeueCondition.signalAll();
            enqueueCondition.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            dequeueLock.unlock();
            enqueueLock.unlock();
        }
    }

    /**
     * Enqueues a new element to the current read index
     *
     * @param element the element to enqueue
     */
    public void enqueue(T element) {
        enqueue(element, false);
    }

    /**
     * Dequeues the current element (specified by the read index)
     *
     * @param abortOnWait let the method return if wait would be called
     * @return the fetched element
     */
    public T dequeue(boolean abortOnWait) {
        T element = null;
        dequeueLock.lock();

        try {
            while (!readable()) {
                dequeueCondition.await();
            }

            element = fields.get(readIndex).data;
            fields.set(readIndex, QueueField.EMPTY);
            LOGGER.info(String.format("Dequeuing element %s from index %d.", element.toString(), readIndex));
            readIndex = (readIndex + 1) % size; // Thread-safe because only here it'll be updated

            dequeueCondition.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            dequeueLock.unlock();
        }

        return element;
    }

    /**
     * Dequeues the current element (specified by the read index)
     *
     * @return the fetched element
     */
    public T dequeue() {
        return dequeue(false);
    }

    /**
     * Checks if the queue is readable at the current read index
     *
     * The condition is fields[readIndex] != null
     *
     * @return true, if readable
     */
    private boolean readable() {
        boolean readable = !isEmpty(readIndex);
        LOGGER.info("readable() returns " + readable + ".");
        return readable;
    }

    /**
     * Checks if the queue is writable at the current write index
     *
     * The condition is fields[writeIndex] == null
     *
     * @return true, if writable
     */
    private boolean writable() {
        boolean writable = isEmpty(writeIndex);
        LOGGER.info("writable() returns " + writable + ".");
        return writable;
    }

    /**
     * Checks if the value at the given index is empty
     *@todo add lock
     * @param index the index of the value to check
     * @return true, if empty
     */
    private boolean isEmpty(int index) {
        boolean ret = fields.get(index).empty;
        return ret;
    }

    /**
     * Helper method for testing purposes. Clears the fields and indexes.
     */
    protected void clear() {
        enqueueLock.lock();
        dequeueLock.lock();
        readIndex = 0;
        writeIndex = 0;
        fields.clear();
        enqueueLock.unlock();
        dequeueLock.unlock();
    }

    /**
     * Thread-safe getter for the contents of a field
     *
     * @param index the index of the field
     * @return the contents of the field specified by the index
     */
    protected T getFieldContent(int index) {
        enqueueLock.lock(); // TODO: Check if safe
        dequeueLock.lock();
        T ret = fields.get(index).data;
        enqueueLock.unlock();
        dequeueLock.unlock();
        return ret;
    }

    /**
     * Returns the current write index
     *
     * @return current write index
     */
    protected int getWriteIndex() {
        enqueueLock.lock();
        int ret = writeIndex;
        enqueueLock.unlock();
        return ret;
    }

    /**
     * Returns the current read index
     *
     * @return current read index
     */
    protected int getReadIndex() {
        dequeueLock.lock();
        int ret = readIndex;
        dequeueLock.unlock();
        return ret;
    }

    private static class QueueField<T> {

        static final QueueField EMPTY = new QueueField();

        T data;
        boolean empty;

        QueueField() {
            empty = true;
        }

        QueueField(T data) {
            this.data = data;
        }
    }
}