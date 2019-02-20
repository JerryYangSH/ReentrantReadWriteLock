package com.jerry.chapter8;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class NonFairReentrantReadWriteLockTest {
    protected static final long MAX_TIMEOUT_MS = 100;

    /**
     * Checks that no one is holding the read lock.
     */
    protected void assertNotReadLocked(FairReentrantReadWriteLock lock) {
        assertEquals(0, lock.getReadLockCount());
        //assertEquals(0L, lock.getOwner());
    }

    /**
     * Checks that no one is holding the write lock.
     */
    protected void assertNotWriteLocked(FairReentrantReadWriteLock lock) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertEquals(0, lock.getWriteHoldCount());
        assertEquals(0L, lock.getOwner());
    }


    protected void assertWriteLockedBySelf(FairReentrantReadWriteLock lock) {
        assertWriteLockedBy(lock, Thread.currentThread().getId());
    }

    /**
     * Checks that lock is write-locked by the given thread.
     */
    protected void assertWriteLockedBy(FairReentrantReadWriteLock lock, long threadId) {
        assertTrue(lock.isWriteLocked());
        assertEquals(threadId, lock.getOwner());
        assertEquals(threadId == Thread.currentThread().getId(),
                lock.isWriteLockedByCurrentThread());
        assertTrue(lock.getWriteHoldCount() > 0);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * Checks that lock is reader-locked by the given number of readers.
     */
    protected void assertReadLockedBy(FairReentrantReadWriteLock lock, int numberOfReader) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertEquals(0, lock.getWriteHoldCount());
        assertEquals(0L, lock.getOwner());
        assertEquals(numberOfReader, lock.getReadLockCount());
    }

    @Test
    public void testBasicLock() {
        FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
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
        FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
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
    public void testWriteUnlock_IMSE() {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
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
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
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
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                boolean gotException = false;
                try {
                    lock.writeLock().lockInterruptibly();
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});

        t.start();
        started.await();
        t.interrupt();
        t.join();
        releaseWriteLock(lock);
    }

    /**
     * Releases write lock, checking that it had a hold count of 1.
     */
    private void releaseWriteLock(FairReentrantReadWriteLock lock) {
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        assertNotWriteLocked(lock);
    }

    @Test
    public void testReadLockInterruptibly_Interruptible() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                boolean gotException = false;
                try {
                    lock.readLock().lockInterruptibly();
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});
        t.start();
        started.await();
        t.interrupt();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLock_Interruptible() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            public void run() {
                started.countDown();
                boolean gotException = false;
                try {
                    lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    gotException = true;
                }
                assertTrue(gotException);
            }});

        t.start();
        started.await();
        t.interrupt();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testWriteTryLock() {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        assertTrue(lock.writeLock().tryLock());
        assertWriteLockedBy(lock, Thread.currentThread().getId());
        assertTrue(lock.writeLock().tryLock());
        assertWriteLockedBy(lock, Thread.currentThread().getId());
        lock.writeLock().unlock();
        releaseWriteLock(lock);
    }

    @Test
    public void testWriteTryLockWhenLocked() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertFalse(lock.writeLock().tryLock());
            }});
        t.start();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLockWhenLocked() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertFalse(lock.readLock().tryLock());
            }});
        t.start();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testMultipleReadLocks() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
                try {
                    assertTrue(lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException ie) {

                }
                lock.readLock().unlock();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        t.start();
        t.join();
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testWriteAfterReadLock() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                started.countDown();
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        t.start();
        started.await();
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());

        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        t.join();
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteAfterMultipleReadLocks() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.readLock().lock();
        lock.readLock().lock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.readLock().lock();
                assertEquals(3, lock.getReadLockCount());
                lock.readLock().unlock();
            }});
        t1.start();
        t1.join();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(2, lock.getReadLockCount());
                started.countDown();
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        t2.start();
        started.await();
        assertNotWriteLocked(lock);
        assertEquals(2, lock.getReadLockCount());
        lock.readLock().unlock();
        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        t2.join();
        assertNotWriteLocked(lock);
    }

    /** <>TODO check if it's fifo</>
     * A thread that tries to acquire a fair read lock (non-reentrantly)
     * will block if there is a waiting writer thread
     */
    @Test
    public void testReaderWriterReaderFairFifo() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final AtomicBoolean t1GotLock = new AtomicBoolean(false);
        final CountDownLatch t1Started = new CountDownLatch(1);
        final CountDownLatch t2Started = new CountDownLatch(1);

        lock.readLock().lock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                t1Started.countDown();
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                t1GotLock.set(true);
                lock.writeLock().unlock();
            }});
        t1.start();
        t1Started.await();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                assertEquals(1, lock.getReadLockCount());
                t2Started.countDown();
                lock.readLock().lock();
                assertEquals(1, lock.getReadLockCount());
                assertTrue(t1GotLock.get());
                lock.readLock().unlock();
            }});
        t2.start();
        t2Started.await();

        assertTrue(t1.isAlive());
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        t1.join();
        t2.join();
        assertNotWriteLocked(lock);
    }

    /**
     * Readlocks succeed only after a writing thread unlocks
     */
    @Test
    public void testReadAfterWriteLock() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        lock.writeLock().lock();
        final CountDownLatch bothStarted = new CountDownLatch(2);
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        t1.start();
        t2.start();
        bothStarted.await();
        releaseWriteLock(lock);
        t1.join();
        t2.join();
        assertNotReadLocked(lock);
    }

    /**
     * Read trylock succeeds if write locked by current thread
     */
    @Test
    public void testReadHoldingWriteLock() {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        lock.writeLock().lock();
        assertTrue(lock.readLock().tryLock());
        lock.readLock().unlock();
        lock.writeLock().unlock();
    }

    @Test
    public void testReadTryLockBarging() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch allStarted = new CountDownLatch(3);
        lock.readLock().lock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                allStarted.countDown();
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        t1.start();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                allStarted.countDown();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        t2.start();

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                allStarted.countDown();
                lock.readLock().tryLock();
                lock.readLock().unlock();
            }});
        t3.start();

        allStarted.await();

        assertTrue(lock.getReadLockCount() > 0);
        t3.join();
        assertTrue(t1.isAlive());
        assertTrue(t2.isAlive());
        lock.readLock().unlock();
        t1.join();
        t2.join();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    /**
     * Read lock succeeds if write locked by current thread even if
     * other threads are waiting for readlock
     */
    @Test
    public void testReadHoldingWriteLock2() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch bothStarted = new CountDownLatch(2);
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        t1.start();
        t2.start();
        bothStarted.await();

        assertWriteLockedBySelf(lock);
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        t1.join();
        t2.join();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testReadHoldingWriteLock3() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch bothStarted = new CountDownLatch(2);
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        t1.start();
        t2.start();
        bothStarted.await();
        assertWriteLockedBySelf(lock);
        lock.readLock().lock();
        lock.readLock().unlock();
        assertWriteLockedBySelf(lock);
        lock.writeLock().unlock();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testWriteHoldingWriteLock4() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch bothStarted = new CountDownLatch(2);
        lock.writeLock().lock();
        lock.writeLock().lock();
        lock.writeLock().unlock();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                bothStarted.countDown();
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        t1.start();
        t2.start();
        bothStarted.await();
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().lock();
        assertWriteLockedBySelf(lock);
        assertEquals(2, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        assertWriteLockedBySelf(lock);
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        t1.join();
        t2.join();
        assertNotReadLocked(lock);
        assertNotWriteLocked(lock);
    }

    @Test
    public void testTryLockWhenReadLocked() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
            }});
        t.start();
        started.await();
        t.join();
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testWriteTryLockWhenReadLock() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.readLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                assertFalse(lock.writeLock().tryLock());
            }});
        t.start();
        started.await();
        t.join();
        assertNotWriteLocked(lock);
        lock.readLock().unlock();
        assertNotReadLocked(lock);
    }

    @Test
    public void testWriteTryLock_Timeout() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                try {
                    long startTime = System.nanoTime();
                    assertFalse(lock.writeLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    assertTrue((System.nanoTime() - startTime) >= MAX_TIMEOUT_MS);
                } catch (InterruptedException ie) {}
            }});
        t.start();
        started.await();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testReadTryLock_Timeout() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
        lock.writeLock().lock();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                started.countDown();
                try {
                    long startTime = System.nanoTime();
                    assertFalse(lock.readLock().tryLock(MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    assertTrue((System.nanoTime() - startTime) >= MAX_TIMEOUT_MS);
                } catch (InterruptedException ie) {}
            }});

        t.start();
        started.await();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testReadLockInterruptibly() throws InterruptedException {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        final CountDownLatch started = new CountDownLatch(1);
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
                started.countDown();
                try {
                    lock.readLock().lockInterruptibly();
                } catch (InterruptedException ie) {

                }
            }});

        t.start();
        started.await();
        t.interrupt();
        t.join();
        releaseWriteLock(lock);
    }

    @Test
    public void testReadLockToString() {
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
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
        final FairReentrantReadWriteLock lock = new FairReentrantReadWriteLock();
        assertTrue(lock.writeLock().toString().contains("Unlocked"));
        lock.writeLock().lock();
        assertTrue(lock.writeLock().toString().contains("Locked by"));
        lock.writeLock().unlock();
        assertTrue(lock.writeLock().toString().contains("Unlocked"));
    }
}
