package com.jerry.chapter8;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assert.*;

public class FairReentrantReadWriteLockIITest {
    protected static final long MAX_TIMEOUT_MS = 200;

    private void startThread(Thread t) {
        t.start();
        final long startTime = System.nanoTime();
        while (!t.isAlive()) {
            if (System.nanoTime() - startTime > MAX_TIMEOUT_MS) {
                throw new AssertionError("timed out");
            }
            Thread.yield();
        }
        assertTrue(t.isAlive());
    }

    private void startAndWaitForQueuedThread(ReentrantReadWriteLock lock, Thread t) {
        t.start();
        final long startTime = System.nanoTime();
        while (!lock.hasQueuedThread(t)) {
            if (System.nanoTime() - startTime > TimeUnit.MILLISECONDS.toNanos(MAX_TIMEOUT_MS)) {
                throw new AssertionError("timed out");
            }
            Thread.yield();
        }
        assertTrue(t.isAlive());
    }
    private void hasQueuedThread(ReentrantReadWriteLock lock, Thread t) {
        assertTrue(lock.hasQueuedThread(t));
        assertTrue(t.isAlive());
    }

    private void startAndWaitForQueuedThread(FairReentrantReadWriteLockII lock, Thread t) {
        t.start();
        final long startTime = System.nanoTime();
        while (!lock.hasQueuedThreads(t.getId())) {
            if (System.nanoTime() - startTime > TimeUnit.MILLISECONDS.toNanos(MAX_TIMEOUT_MS)) {
                throw new AssertionError("timed out");
            }
            Thread.yield();
        }
        assertTrue(t.isAlive());
        assertNotEquals(t.getId(), lock.getOwner());
    }
    private void hasQueuedThread(FairReentrantReadWriteLockII lock, Thread t) {
        assertTrue(lock.hasQueuedThreads(t.getId()));
        assertTrue(t.isAlive());
    }

    private void awaitTermination(Thread t) throws InterruptedException {
        t.join();
        assertFalse(t.isAlive());
    }

    /**
     * Checks that no one is holding the read lock.
     */
    protected void assertNotReadLocked(FairReentrantReadWriteLockII lock) {
        assertEquals(0, lock.getReadLockCount());
        //assertEquals(0L, lock.getOwner());
    }

    /**
     * Checks that no one is holding the write lock.
     */
    protected void assertNotWriteLocked(ReentrantReadWriteLock lock) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertEquals(0, lock.getWriteHoldCount());
    }

    /**
     * Checks that no one is holding the write lock.
     */
    protected void assertNotWriteLocked(FairReentrantReadWriteLockII lock) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertEquals(0, lock.getWriteHoldCount());
        assertEquals(0L, lock.getOwner());
    }

    protected void assertWriteLockedBySelf(ReentrantReadWriteLock lock) {
        assertTrue(lock.isWriteLocked());
        assertTrue(lock.isWriteLockedByCurrentThread());
        assertTrue(lock.getWriteHoldCount() > 0);
        assertEquals(0, lock.getReadLockCount());
    }

    protected void assertWriteLockedBySelf(FairReentrantReadWriteLockII lock) {
        assertWriteLockedBy(lock, Thread.currentThread().getId());
    }
    /**
     * Checks that lock is write-locked by the given thread.
     */
    private void assertWriteLockedBy(FairReentrantReadWriteLockII lock, long threadId) {
        assertTrue(lock.isWriteLocked());
        assertEquals(threadId, lock.getOwner());
        assertEquals(threadId == Thread.currentThread().getId(),
                lock.isWriteLockedByCurrentThread());
        assertEquals(threadId == Thread.currentThread().getId(),
                lock.getWriteHoldCount() > 0);
        assertEquals(threadId == Thread.currentThread().getId(),
                lock.getWriteHoldCount() > 0);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * Checks that lock is reader-locked by the given number of readers.
     */
    protected void assertReadLockedBy(FairReentrantReadWriteLockII lock, int numberOfReader) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertEquals(0, lock.getWriteHoldCount());
        assertEquals(0L, lock.getOwner());
        assertEquals(numberOfReader, lock.getReadLockCount());
    }

    @Test
    public void testBasicLock() {
        FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);

        lock.readLock().lock();
        assertNotWriteLocked(lock);
        assertReadLockedBy(lock, 1);
        lock.readLock().unlock();

        lock.writeLock().lock();
        assertNotReadLocked(lock);
        assertWriteLockedBySelf(lock);
        lock.writeLock().unlock();
    }

    @Test
    public void testGetWriteHoldCount() {
        final int DEPTH = 10;
        FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        for (int i = 1;i <= DEPTH; i++) {
            lock.writeLock().lock();
            assertWriteLockedBySelf(lock);
            assertEquals(i, lock.getWriteHoldCount());
        }
        for (int i = DEPTH; i > 0; i--) {
            assertWriteLockedBySelf(lock);
            lock.writeLock().unlock();
            assertEquals(i - 1, lock.getWriteHoldCount());
        }
        assertNotWriteLocked(lock);
    }

    @Test
    public void testMixedReadWriteReentrantLock() {
        testMixedReadWriteReentrantLock(true);
        testMixedReadWriteReentrantLock(false);
    }
    private void testMixedReadWriteReentrantLock(boolean readFirst) {
        final int DEPTH = 10;
        FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        for (int i = 1; i <= DEPTH; i++) {
            if (readFirst) {
                lock.readLock().lock();
                lock.writeLock().lock();
            } else {
                lock.writeLock().lock();
                lock.readLock().lock();
            }
            assertWriteLockedBySelf(lock);
            assertEquals(i, lock.getWriteHoldCount());
        }
        for (int i = DEPTH; i > 0; i--) {
            if (readFirst) {
                lock.readLock().unlock();
                assertWriteLockedBySelf(lock);
                lock.writeLock().unlock();
            } else {
                assertWriteLockedBySelf(lock);
                lock.writeLock().unlock();
                lock.readLock().unlock();
            }
            assertEquals(i - 1, lock.getWriteHoldCount());
        }
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteUnlock_IMSE() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        boolean gotException = false;
        try {
            lock.writeLock().unlock();
        } catch (IllegalMonitorStateException imse) {
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public void testReadUnlock_IMSE() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        boolean gotException = false;
        try {
            lock.readLock().unlock();
        } catch (IllegalMonitorStateException imse) {
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public void testWriteLockInterruptibly_Interruptible() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotException = false;
                try {
                    lock.writeLock().lockInterruptibly();
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});

        startAndWaitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testWriteLock_Uninterruptible() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotException = false;
                try {
                    lock.writeLock().lock();
                    lock.writeLock().unlock();
                } catch (IllegalMonitorStateException e) {
                    gotException = true;
                }
                assertFalse(gotException);
            }});

        startAndWaitForQueuedThread(lock, t);
        t.interrupt();
        Thread.sleep(MAX_TIMEOUT_MS);
        hasQueuedThread(lock, t);
        releaseWriteLock(lock);
        awaitTermination(t);
    }


    /**
     * Releases write lock, checking that it had a hold count of 1.
     */
    private void releaseWriteLock(ReentrantReadWriteLock lock) {
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        assertNotWriteLocked(lock);
    }

    /**
     * Releases write lock, checking that it had a hold count of 1.
     */
    private void releaseWriteLock(FairReentrantReadWriteLockII lock) {
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        assertNotWriteLocked(lock);
    }

    @Test
    public void testReadLockInterruptibly_Interruptible() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotException = false;
                try {
                    lock.readLock().lockInterruptibly();
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});
        startAndWaitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLock_Interruptible() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotException = false;
                try {
                    lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});

        startAndWaitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testWriteTryLock() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        assertTrue(lock.writeLock().tryLock());
        assertWriteLockedBy(lock, Thread.currentThread().getId());
        assertTrue(lock.writeLock().tryLock());
        assertWriteLockedBy(lock, Thread.currentThread().getId());
        lock.writeLock().unlock();
        releaseWriteLock(lock);
    }

    @Test
    public void testWriteTryLockWhenLocked() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertFalse(lock.writeLock().tryLock());
            }});
        startThread(t);
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLockWhenLocked() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertFalse(lock.readLock().tryLock());
            }});
        startThread(t);
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testMultipleReadLocks() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotInterrupted = false;
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
                try {
                    assertTrue(lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException ie) {
                    gotInterrupted = true;
                }
                assertFalse(gotInterrupted);
                lock.readLock().unlock();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        startThread(t);
        awaitTermination(t);
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testMultipleReadLocks2() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        final CountDownLatch checkPoint1 = new CountDownLatch(1);
        final CountDownLatch checkPoint2 = new CountDownLatch(1);
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gotInterrupted = false;
                try {
                    assertTrue(lock.readLock().tryLock());
                    assertReadLockedBy(lock, 2);
                    checkPoint1.countDown();
                    checkPoint2.await();
                    lock.readLock().unlock();

                    assertTrue(lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    lock.readLock().unlock();

                    lock.readLock().lock();
                    lock.readLock().unlock();
                } catch (InterruptedException ie) {
                    gotInterrupted = true;
                }
                assertFalse(gotInterrupted);
            }});
        startThread(t);
        checkPoint1.await();
        lock.readLock().unlock();
        checkPoint2.countDown();
        awaitTermination(t);
        assertNotReadLocked(lock);
    }


    @Test
    public void testWriteAfterReadLock() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        startAndWaitForQueuedThread(lock, t);
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());

        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        awaitTermination(t);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteAfterMultipleReadLocks() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        lock.readLock().lock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().lock();
                assertEquals(3, lock.getReadLockCount());
                lock.readLock().unlock();
            }});
        startThread(t1);
        awaitTermination(t1);

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(2, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        startAndWaitForQueuedThread(lock,t2);
        assertNotWriteLocked(lock);
        assertEquals(2, lock.getReadLockCount());
        lock.readLock().unlock();
        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        awaitTermination(t2);
        assertNotWriteLocked(lock);
    }

    /**
     * A thread that tries to acquire a fair read lock (non-reentrantly)
     * will block if there is a waiting writer thread
     */
    @Test
    public void testReaderWriterReaderFairFifo() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        final AtomicBoolean t1GotLock = new AtomicBoolean(false);

        lock.readLock().lock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                t1GotLock.set(true);
                lock.writeLock().unlock();
            }});
        startAndWaitForQueuedThread(lock,t1);

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                lock.readLock().lock();
                assertEquals(1, lock.getReadLockCount());
                assertTrue(t1GotLock.get());
                lock.readLock().unlock();
            }});
        startAndWaitForQueuedThread(lock,t2);

        assertTrue(t1.isAlive());
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotWriteLocked(lock);
    }

    /**
     * Readlocks succeed only after a writing thread unlocks
     */
    @Test
    public void testReadAfterWriteLock() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        final long mainThreadId = Thread.currentThread().getId();
        lock.writeLock().lock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertWriteLockedBy(lock, mainThreadId);
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertWriteLockedBy(lock, mainThreadId);
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        startAndWaitForQueuedThread(lock,t1);
        startAndWaitForQueuedThread(lock,t2);
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotReadLocked(lock);
    }

    /**
     * Read trylock succeeds if write locked by current thread
     */
    @Test
    public void testReadHoldingWriteLock() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        assertTrue(lock.readLock().tryLock());
        lock.readLock().unlock();
        lock.writeLock().unlock();
    }

    @Test
    public void testReadTryLockBarging() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        startAndWaitForQueuedThread(lock,t1);

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        startAndWaitForQueuedThread(lock,t2);

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().tryLock();
                lock.readLock().unlock();
            }});
        startThread(t3);

        assertTrue(lock.getReadLockCount() > 0);
        awaitTermination(t3);
        assertTrue(t1.isAlive());
        assertTrue(t2.isAlive());
        lock.readLock().unlock();
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    /**
     * Read lock succeeds if write locked by current thread even if
     * other threads are waiting for readlock
     */
    @Test
    public void testReadHoldingWriteLock2() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        startAndWaitForQueuedThread(lock,t1);
        startAndWaitForQueuedThread(lock,t2);

        assertWriteLockedBySelf(lock);
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testReadHoldingWriteLock3() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        startAndWaitForQueuedThread(lock,t1);
        startAndWaitForQueuedThread(lock,t2);
        assertWriteLockedBySelf(lock);
        lock.readLock().lock();
        lock.readLock().unlock();
        assertWriteLockedBySelf(lock);
        lock.writeLock().unlock();
        assertNotReadLocked(lock);
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteHoldingWriteLock4() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        lock.writeLock().lock();
        lock.writeLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        startAndWaitForQueuedThread(lock,t1);
        startAndWaitForQueuedThread(lock,t2);
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().lock();
        assertWriteLockedBySelf(lock);
        assertEquals(2, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testTryLockWhenReadLocked() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
            }});
        startThread(t);
        awaitTermination(t);
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testWriteTryLockWhenReadLocked() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertFalse(lock.writeLock().tryLock());
            }});
        startThread(t);
        awaitTermination(t);
        assertNotWriteLocked(lock);
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testWriteTryLockWhenReadLocked2() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.readLock().lock();
        assertTrue(lock.writeLock().tryLock());
        assertWriteLockedBySelf(lock);
        lock.readLock().unlock();
        lock.writeLock().unlock();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteTryLock_Timeout() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    assertFalse(lock.writeLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    assertTrue((System.nanoTime() - startTime) >= MAX_TIMEOUT_MS);
                } catch (InterruptedException ie) {}
            }});
        startThread(t);
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLock_Timeout() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    assertFalse(lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    assertTrue((System.nanoTime() - startTime) >= MAX_TIMEOUT_MS);
                } catch (InterruptedException ie) {}
            }});

        startThread(t);
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testReadLockInterruptibly() throws InterruptedException {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        try {
            lock.readLock().lockInterruptibly();
            lock.readLock().unlock();
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException ie) {
            fail("interrupted exception should not raise here " + ie.getMessage());
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.readLock().lockInterruptibly();
                } catch (InterruptedException ie) {

                }
            }});

        startAndWaitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    @Test
    public void testReadLockToString() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        assertTrue(lock.readLock().toString().contains("Read locks = 0"));
        lock.readLock().lock();
        assertTrue(lock.readLock().toString().contains("Read locks = 1"));
        lock.readLock().lock();
        assertTrue(lock.readLock().toString().contains("Read locks = 2"));
        lock.readLock().unlock();
        assertTrue(lock.readLock().toString().contains("Read locks = 1"));
        lock.readLock().unlock();
        assertTrue(lock.readLock().toString().contains("Read locks = 0"));
    }

    @Test
    public void testWriteLockToString() {
        final FairReentrantReadWriteLockII lock = new FairReentrantReadWriteLockII();
        assertTrue(lock.writeLock().toString().contains("Unlocked"));
        lock.writeLock().lock();
        assertTrue(lock.writeLock().toString().contains("Locked by"));
        lock.writeLock().unlock();
        assertTrue(lock.writeLock().toString().contains("Unlocked"));
    }
}
