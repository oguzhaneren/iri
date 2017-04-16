package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {

    private static final TransactionRequester instance = new TransactionRequester();

    private final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private static double P_REMOVE_REQUEST;
    private final Set<Hash> transactionsToRequest = new HashSet<>();
    private final SecureRandom random = new SecureRandom();
    private volatile long lastTime = System.currentTimeMillis();
    public  static final int REQUEST_HASH_SIZE = 46;
    private static final byte[] NULL_REQUEST_HASH_BYTES = new byte[REQUEST_HASH_SIZE];

    public void init(double p_REMOVE_REQUEST) {
        P_REMOVE_REQUEST = p_REMOVE_REQUEST;
    }

    public void rescanTransactionsToRequest() throws ExecutionException, InterruptedException {
        Hash[] missingTx = TransactionViewModel.getMissingTransactions();
        synchronized (this) {
            transactionsToRequest.clear();
            transactionsToRequest.addAll(Arrays.asList(missingTx));
        }
    }
    public Hash[] getRequestedTransactions() {
        return transactionsToRequest.stream().toArray(Hash[]::new);
    }

    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size();
    }

    void clearTransactionRequest(Hash hash) {
        synchronized (this) {
            transactionsToRequest.remove(hash);
        }
    }

    public void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(hash)) {
            synchronized (this) {
                transactionsToRequest.add(hash);
            }
        }
    }
    public void transactionToRequest(byte[] buffer, int offset) throws Exception {
        final long beginningTime = System.currentTimeMillis();
        Hash hash = null;
        if(transactionsToRequest.size() > 0) {
            while(hash == null) {
                hash = ((Hash) transactionsToRequest.toArray()[random.nextInt(transactionsToRequest.size())]);
                if(TransactionViewModel.exists(hash)) {
                    synchronized (this) {
                        log.info("Removing existing tx from request list: " + hash);
                        transactionsToRequest.remove(hash);
                    }
                    hash = null;
                }
                if(transactionsToRequest.size() == 0) {
                    break;
                }
            }
        }

        if(hash != null && !hash.equals(Hash.NULL_HASH)) {
            if(random.nextDouble() < P_REMOVE_REQUEST) {
                synchronized (this) {
                    transactionsToRequest.remove(hash);
                }
            }
            System.arraycopy(hash.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        } else {
            System.arraycopy(Hash.NULL_HASH.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", transactionsToRequest.size() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }

    public static TransactionRequester instance() {
        return instance;
    }
}