package com.notnoop.apns.integration;

import static com.notnoop.apns.utils.FixedCertificates.*;

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
import com.notnoop.apns.SimpleApnsNotification;
import com.notnoop.apns.internal.netty.util.MockApnsServer;
import com.notnoop.apns.utils.FixedCertificates;

public class FixedApnsConnectionCacheTest {

    MockApnsServer server;
    static SimpleApnsNotification msg1 = new SimpleApnsNotification(
            "a87d8878d878a79", "{\"aps\":{}}");
    static SimpleApnsNotification msg2 = new SimpleApnsNotification(
            "a87d8878d878a88", "{\"aps\":{}}");
    static EnhancedApnsNotification eMsg1 = new EnhancedApnsNotification(
            EnhancedApnsNotification.INCREMENT_ID(), 1, "a87d8878d878a88",
            "{\"aps\":{}}");
    static EnhancedApnsNotification eMsg2 = new EnhancedApnsNotification(
            EnhancedApnsNotification.INCREMENT_ID(), 1, "a87d8878d878a88",
            "{\"aps\":{}}");
    static EnhancedApnsNotification eMsg3 = new EnhancedApnsNotification(
            EnhancedApnsNotification.INCREMENT_ID(), 1, "a87d8878d878a88",
            "{\"aps\":{}}");

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

    /**
     * Test1 to make sure that after rejected notification in-flight
     * notifications are re-transmitted
     * 
     * @throws InterruptedException
     */
    @Test(timeout = 50000000)
    public void handleReTransmissionError5Good1Bad7Good()
            throws InterruptedException {

        // 5 success 1 fail 7 success 7 resent
        final CountDownLatch sync = new CountDownLatch(20);
        final AtomicInteger numResent = new AtomicInteger();
        final AtomicInteger numSent = new AtomicInteger();
        int EXPECTED_RESEND_COUNT = 7;
        int EXPECTED_SEND_COUNT = 12;
        ApnsService service = APNS.newService().withSSLContext(clientContext())
                .withGatewayDestination(TEST_HOST, TEST_GATEWAY_PORT)
                .withDelegate(new ApnsDelegate() {
                    @Override
                    public void messageSent(ApnsNotification message,
                            boolean resent) {
                        if (!resent) {
                            numSent.incrementAndGet();
                        }
                        sync.countDown();
                    }

                    @Override
                    public void messageSendFailed(ApnsNotification message,
                            Throwable e) {
                        numSent.decrementAndGet();
                    }

                    @Override
                    public void connectionClosed(DeliveryError e,
                            int messageIdentifier) {
                    }

                    @Override
                    public void cacheLengthExceeded(int newCacheLength) {
                    }

                    @Override
                    public void notificationsResent(int resendCount) {
                        numResent.set(resendCount);
                    }
                }).build();
        server.failWithErrorAfterNotifications(DeliveryError.ofCode(8), 6, null);
        for (int i = 0; i < 5; ++i) {
            service.push(eMsg1);
        }

        service.push(eMsg2);

        for (int i = 0; i < 7; ++i) {
            service.push(eMsg3);
        }

        sync.await();

        Assert.assertEquals(EXPECTED_RESEND_COUNT, numResent.get());
        Assert.assertEquals(EXPECTED_SEND_COUNT, numSent.get());

        service.stop();
    }
}
