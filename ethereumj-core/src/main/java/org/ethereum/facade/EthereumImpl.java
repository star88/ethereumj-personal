package org.ethereum.facade;

import org.ethereum.core.Transaction;
import org.ethereum.core.Wallet;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.BlockLoader;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.peerdiscovery.PeerInfo;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.net.submit.TransactionExecutor;
import org.ethereum.net.submit.TransactionTask;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static org.ethereum.config.SystemProperties.CONFIG;

/**
 * @author Roman Mandeleil
 * @since 27.07.2014
 */
@Singleton
public class EthereumImpl implements Ethereum {

    private static final Logger logger = LoggerFactory.getLogger("facade");

    EthereumListener listener;

    WorldManager worldManager;

    AdminInfo adminInfo;

    ChannelManager channelManager;

    PeerServer peerServer;

    BlockLoader blockLoader;

    Provider<PeerClient> peerClientProvider;

    @Inject
    public EthereumImpl(WorldManager worldManager, AdminInfo adminInfo,
                        ChannelManager channelManager, BlockLoader blockLoader,
                        Provider<PeerClient> peerClientProvider, EthereumListener listener) {
        System.out.println();
		logger.info("EthereumImpl constructor");
        this.worldManager = worldManager;
        this.adminInfo = adminInfo;
        this.channelManager = channelManager;
        this.blockLoader = blockLoader;
        this.peerClientProvider = peerClientProvider;
        this.listener = listener;

        this.init();
    }

    public void init() {
        worldManager.loadBlockchain();
        if (CONFIG.listenPort() > 0) {
            Executors.newSingleThreadExecutor().submit(
                    new Runnable() {
                        public void run() {
//                            peerServer.start(CONFIG.listenPort());
                        }
                    }
            );
        }
    }

    /**
     * Find a peer but not this one
     *
     * @param peer - peer to exclude
     * @return online peer
     */
    @Override
    public PeerInfo findOnlinePeer(PeerInfo peer) {
        Set<PeerInfo> excludePeers = new HashSet<>();
        excludePeers.add(peer);
        return findOnlinePeer(excludePeers);
    }

    @Override
    public PeerInfo findOnlinePeer() {
        Set<PeerInfo> excludePeers = new HashSet<>();
        return findOnlinePeer(excludePeers);
    }

    @Override
    public PeerInfo findOnlinePeer(Set<PeerInfo> excludePeers) {
        logger.info("Looking for online peers...");

        final EthereumListener listener = this.listener;
        listener.trace("Looking for online peer");

        worldManager.startPeerDiscovery();

        final Set<PeerInfo> peers = worldManager.getPeerDiscovery().getPeers();
        for (PeerInfo peer : peers) { // it blocks until a peer is available.
            if (peer.isOnline() && !excludePeers.contains(peer)) {
                logger.info("Found peer: {}", peer.toString());
                listener.trace(String.format("Found online peer: [ %s ]", peer.toString()));
                return peer;
            }
        }
        return null;
    }

    @Override
    public PeerInfo waitForOnlinePeer() {
        PeerInfo peer = null;
        while (peer == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            peer = this.findOnlinePeer();
        }
        return peer;
    }

    @Override
    public Set<PeerInfo> getPeers() {
        return worldManager.getPeerDiscovery().getPeers();
    }

    @Override
    public void startPeerDiscovery() {
        worldManager.startPeerDiscovery();
    }

    @Override
    public void stopPeerDiscovery() {
        worldManager.stopPeerDiscovery();
    }

    @Override
    public void connect(InetAddress addr, int port, String remoteId) {
        connect(addr.getHostName(), port, remoteId);
    }

    @Override
    public void connect(String ip, int port, String remoteId) {
        logger.info("Connecting to: {}:{}", ip, port);

        PeerClient peerClient = worldManager.getActivePeer();
        if (peerClient == null)
            peerClient = peerClientProvider.get();
        worldManager.setActivePeer(peerClient);

        peerClient.connect(ip, port, remoteId);
    }

    @Override
    public Blockchain getBlockchain() {
        return worldManager.getBlockchain();
    }

    @Override
    public void addListener(EthereumListener listener) {
        worldManager.addListener(listener);
    }

    @Override
    public boolean isBlockchainLoading() {
        return worldManager.isBlockchainLoading();
    }

    @Override
    public void close() {
        worldManager.close();
    }

    @Override
    public PeerClient getDefaultPeer() {

        PeerClient peer = worldManager.getActivePeer();
        if (peer == null) {

            peer = peerClientProvider.get();
            worldManager.setActivePeer(peer);
        }
        return peer;
    }

    @Override
    public boolean isConnected() {
        return worldManager.getActivePeer() != null;
    }

    @Override
    public Transaction createTransaction(BigInteger nonce,
                                         BigInteger gasPrice,
                                         BigInteger gas,
                                         byte[] receiveAddress,
                                         BigInteger value, byte[] data) {

        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] gasPriceBytes = ByteUtil.bigIntegerToBytes(gasPrice);
        byte[] gasBytes = ByteUtil.bigIntegerToBytes(gas);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);

        return new Transaction(nonceBytes, gasPriceBytes, gasBytes,
                receiveAddress, valueBytes, data);
    }


    @Override
    public Future<Transaction> submitTransaction(Transaction transaction) {

        TransactionTask transactionTask = new TransactionTask(transaction, channelManager);

        return TransactionExecutor.instance.submitTransaction(transactionTask);
    }


    @Override
    public Wallet getWallet() {
        return worldManager.getWallet();
    }


    @Override
    public Repository getRepository() {
        return worldManager.getRepository();
    }

    @Override
    public AdminInfo getAdminInfo() {
        return adminInfo;
    }

    @Override
    public ChannelManager getChannelManager() {
        return channelManager;
    }


    @Override
    public Set<Transaction> getPendingTransactions() {
        return getBlockchain().getPendingTransactions();
    }

    @Override
    public BlockLoader getBlockLoader(){
        return  blockLoader;
    }

    @Override
    public void exitOn(long number) {
        worldManager.getBlockchain().setExitOn(number);
    }

    @Override
    public EthereumListener getListener() {
        return listener;
    }
}