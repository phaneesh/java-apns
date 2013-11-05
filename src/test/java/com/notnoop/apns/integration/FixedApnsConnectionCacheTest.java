package com.notnoop.apns.integration;

import static com.notnoop.apns.utils.FixedCertificates.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.internal.netty.util.MockApnsServer;
import com.notnoop.apns.utils.FixedCertificates;

public class FixedApnsConnectionCacheTest {

    MockApnsServer server;

    @Before
    public void startup() throws InterruptedException {
        server = new MockApnsServer(TEST_GATEWAY_PORT,
                FixedCertificates.serverContext());
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
        server = null;
    }

    @Test(timeout = 5000)
    public void test_send_50_no_failure() {
        ConnectionCacheTest test = new ConnectionCacheTest();
        test.setError(DeliveryError.MISSING_DEVICE_TOKEN);
        test.setExpectedClosedConnections(0);
        test.setExpectedResent(0);
        test.setExpectedSent(50);
        test.setExpectedTotal(50);
        test.setIdToFail(null);
        test.setFailWhenReceive(null);
        test(test);
    }

    @Test(timeout = 500000)
    public void test_20_fails_id_10_after_receiving_15() throws InterruptedException {
        ConnectionCacheTest test = new ConnectionCacheTest();
        test.setError(DeliveryError.MISSING_DEVICE_TOKEN);
        test.setExpectedClosedConnections(1);
        test.setExpectedResent(5);
        test.setExpectedSent(20);
        test.setExpectedTotal(20);
        test.setIdToFail(10);
        test.setFailWhenReceive(15);
        test(test);
        Thread.sleep(20000);
    }

    protected void test(ConnectionCacheTest test) {
        final CountDownLatch sync = new CountDownLatch(test.getExpectedTotal());

        final AtomicInteger numResent = new AtomicInteger();
        final AtomicInteger numSent = new AtomicInteger();
        final AtomicInteger numConnectionClosed = new AtomicInteger();

        ApnsService service = buildApnsService(new ApnsDelegate() {
            @Override
            public void messageSent(ApnsNotification message, boolean resent) {
                if (!resent) {
                    numSent.incrementAndGet();
                }
                sync.countDown();
            }

            @Override
            public void messageSendFailed(ApnsNotification message, Throwable e) {
                numSent.decrementAndGet();
            }

            @Override
            public void connectionClosed(DeliveryError e, int messageIdentifier) {
                numConnectionClosed.incrementAndGet();
            }

            @Override
            public void cacheLengthExceeded(int newCacheLength) {
            }

            @Override
            public void notificationsResent(int resendCount) {
                numResent.set(resendCount);
            }
        });
        if (test.getIdToFail() != null && test.getFailWhenReceive() != null)
            server.fail(DeliveryError.MISSING_DEVICE_TOKEN, test.getIdToFail(),
                    test.getFailWhenReceive());

        test.act(service);

        try {
            sync.await();
        } catch (InterruptedException e1) {
            throw new RuntimeException(e1);
        }
        service.stop();
//        Assert.assertEquals(test.getExpectedSent(), numSent.get());
//        Assert.assertTrue(test.getExpectedResent() <= numResent.get());
//        Assert.assertEquals(test.getExpectedClosedConnections(),
//                numConnectionClosed.get());
        List<List<Integer>> receivedIds = server.getReceivedNotificationIds();

        // Assert.assertEquals(test.getExpectedClosedConnections(),
        // receivedIds.size() - 1);

        System.out.println(receivedIds);
        if (test.getIdToFail() != null && test.getFailWhenReceive() != null) {
            for (int i = 0; i <= test.getFailWhenReceive(); i++) {
                Assert.assertTrue(receivedIds.get(0).contains(i));
            }
            for (int i = test.getIdToFail() + 1; i < test.getExpectedTotal(); i++) {
                System.out.println(i);
                Assert.assertTrue(receivedIds.get(1).contains(i));
            }
        }
    }

    private ApnsService buildApnsService(ApnsDelegate apnsDelegate) {
        return APNS.newService().withSSLContext(clientContext())
                .withGatewayDestination(TEST_HOST, TEST_GATEWAY_PORT)
                .withDelegate(apnsDelegate).build();
    }

    public static class ConnectionCacheTest {
        static EnhancedApnsNotification[] MESSAGES = new EnhancedApnsNotification[100];

        static {
            for (int i = 0; i < MESSAGES.length; i++) {
                MESSAGES[i] = new EnhancedApnsNotification(i, 1,
                        "a87d8878d878a88", "{\"aps\":{}}");
            }
        }

        private DeliveryError error;
        private int expectedClosedConnections;
        private int expectedTotal;
        private int expectedSent;
        private int expectedResent;
        private Integer idToFail;
        private Integer failWhenReceive;

        public DeliveryError getError() {
            return error;
        }

        public void setError(DeliveryError error) {
            this.error = error;
        }

        public int getExpectedClosedConnections() {
            return expectedClosedConnections;
        }

        public void setExpectedClosedConnections(int expectedClosedConnections) {
            this.expectedClosedConnections = expectedClosedConnections;
        }

        public int getExpectedTotal() {
            return expectedTotal;
        }

        public void setExpectedTotal(int expectedTotal) {
            this.expectedTotal = expectedTotal;
        }

        public int getExpectedSent() {
            return expectedSent;
        }

        public void setExpectedSent(int expectedSent) {
            this.expectedSent = expectedSent;
        }

        public int getExpectedResent() {
            return expectedResent;
        }

        public void setExpectedResent(int expectedResent) {
            this.expectedResent = expectedResent;
        }

        public Integer getIdToFail() {
            return idToFail;
        }

        public void setIdToFail(Integer idToFail) {
            this.idToFail = idToFail;
        }

        public Integer getFailWhenReceive() {
            return failWhenReceive;
        }

        public void setFailWhenReceive(Integer failWhenReceive) {
            this.failWhenReceive = failWhenReceive;
        }

        public void act(ApnsService apnsService) {
            for (int i = 0; i < expectedTotal; i++) {
                apnsService.push(MESSAGES[i]);
            }
        }

    }
}
