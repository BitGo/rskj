/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core;

import co.rsk.blocks.FileBlockPlayer;
import co.rsk.blocks.FileBlockRecorder;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.*;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.net.handler.TxHandlerImpl;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.core.PendingState;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.handler.EthHandlerFactoryImpl;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.HandshakeHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.server.*;
import org.ethereum.sync.SyncPool;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
@ComponentScan("org.ethereum")
public class RskFactory {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public Rsk getRsk(WorldManager worldManager,
                      AdminInfo adminInfo,
                      ChannelManager channelManager,
                      PeerServer peerServer,
                      ProgramInvokeFactory programInvokeFactory,
                      PendingState pendingState,
                      SystemProperties config,
                      CompositeEthereumListener compositeEthereumListener,
                      ReceiptStore receiptStore,
                      PeerScoringManager peerScoringManager,
                      NodeBlockProcessor nodeBlockProcessor,
                      NodeMessageHandler nodeMessageHandler) {

        logger.info("Running {},  core version: {}-{}", config.genesisInfo(), config.projectVersion(), config.projectVersionModifier());
        BuildInfo.printInfo();

        RskImpl rsk = new RskImpl(worldManager, adminInfo, channelManager, peerServer, programInvokeFactory,
                pendingState, config, compositeEthereumListener, receiptStore, peerScoringManager, nodeBlockProcessor, nodeMessageHandler);

        rsk.init();
        rsk.getBlockchain().setRsk(true);  //TODO: check if we can remove this field from org.ethereum.facade.Blockchain
        if (logger.isInfoEnabled()) {
            String versions = EthVersion.supported().stream().map(EthVersion::name).collect(Collectors.joining(", "));
            logger.info("Capability eth version: [{}]", versions);
        }
        if (RskSystemProperties.CONFIG.isBlocksEnabled()) {
            setupRecorder(rsk, RskSystemProperties.CONFIG.blocksRecorder());
            setupPlayer(rsk, RskSystemProperties.CONFIG.blocksPlayer());
        }
        return rsk;
    }

    private void setupRecorder(RskImpl rsk, String blocksRecorderFileName) {
        if (blocksRecorderFileName != null) {
            rsk.getBlockchain().setBlockRecorder(new FileBlockRecorder(blocksRecorderFileName));
        }
    }

    private void setupPlayer(RskImpl rsk, String blocksPlayerFileName) {
        if (blocksPlayerFileName == null) {
            return;
        }

        new Thread(() -> {
            try (FileBlockPlayer bplayer = new FileBlockPlayer(blocksPlayerFileName)) {
                rsk.setIsPlayingBlocks(true);

                Blockchain bc = rsk.getWorldManager().getBlockchain();
                ChannelManager cm = rsk.getChannelManager();

                for (Block block = bplayer.readBlock(); block != null; block = bplayer.readBlock()) {
                    ImportResult tryToConnectResult = bc.tryToConnect(block);
                    if (BlockProcessResult.importOk(tryToConnectResult)) {
                        cm.broadcastBlock(block, null);
                    }
                }
            } catch (Exception e) {
                logger.error("Error", e);
            } finally {
                rsk.setIsPlayingBlocks(false);
            }
        }).start();
    }

    @Bean
    public PeerScoringManager getPeerScoringManager(SystemProperties config) {
        int nnodes = config.scoringNumberOfNodes();

        long nodePunishmentDuration = config.scoringNodesPunishmentDuration();
        int nodePunishmentIncrement = config.scoringNodesPunishmentIncrement();
        long nodePunhishmentMaximumDuration = config.scoringNodesPunishmentMaximumDuration();

        long addressPunishmentDuration = config.scoringAddressesPunishmentDuration();
        int addressPunishmentIncrement = config.scoringAddressesPunishmentIncrement();
        long addressPunishmentMaximunDuration = config.scoringAddressesPunishmentMaximumDuration();

        return new PeerScoringManager(nnodes, new PunishmentParameters(nodePunishmentDuration, nodePunishmentIncrement,
                nodePunhishmentMaximumDuration), new PunishmentParameters(addressPunishmentDuration, addressPunishmentIncrement, addressPunishmentMaximunDuration));
    }

    @Bean
    public NodeBlockProcessor getNodeBlockProcessor(Blockchain blockchain, BlockStore blockStore, BlockNodeInformation blockNodeInformation, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration) {
        return new NodeBlockProcessor(blockStore, blockchain, blockNodeInformation, blockSyncService, syncConfiguration);
    }

    @Bean
    public SyncProcessor getSyncProcessor(WorldManager worldManager,
                                          BlockSyncService blockSyncService,
                                          PeerScoringManager peerScoringManager,
                                          SyncConfiguration syncConfiguration) {
        return new SyncProcessor(worldManager.getBlockchain(), blockSyncService, peerScoringManager, syncConfiguration, new ProofOfWorkRule());
    }

    @Bean
    public BlockSyncService getBlockSyncService(Blockchain blockchain,
                                                BlockStore store,
                                                BlockNodeInformation nodeInformation,
                                                SyncConfiguration syncConfiguration,
                                                ChannelManager channelManager) {
            return new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration, channelManager);
    }

    @Bean
    public NodeMessageHandler getNodeMessageHandler(NodeBlockProcessor nodeBlockProcessor,
                                                    SyncProcessor syncProcessor,
                                                    ChannelManager channelManager,
                                                    WorldManager worldManager,
                                                    PeerScoringManager peerScoringManager) {

        NodeMessageHandler nodeMessageHandler = new NodeMessageHandler(nodeBlockProcessor,
                syncProcessor,
                channelManager,
                worldManager.getPendingState(),
                new TxHandlerImpl(worldManager),
                peerScoringManager,
                new ProofOfWorkRule());

        nodeMessageHandler.start();
        return nodeMessageHandler;
    }

    @Bean
    public SyncPool getSyncPool(EthereumListener ethereumListener, Blockchain blockchain, SystemProperties config, NodeManager nodeManager, SyncPool.PeerClientFactory peerClientFactory) {
        return new SyncPool(ethereumListener, blockchain, config, nodeManager, peerClientFactory);
    }

    @Bean
    public SyncPool.PeerClientFactory getPeerClientFactory(SystemProperties config,
                                                           EthereumListener ethereumListener,
                                                           EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return () -> new PeerClient(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public EthereumChannelInitializerFactory getEthereumChannelInitializerFactory(ChannelManager channelManager, EthereumChannelInitializer.ChannelFactory channelFactory) {
        return remoteId -> new EthereumChannelInitializer(remoteId, channelManager, channelFactory);
    }

    @Bean
    public EthereumChannelInitializer.ChannelFactory getChannelFactory(SystemProperties config,
                                                                       EthereumListener ethereumListener,
                                                                       ConfigCapabilities configCapabilities,
                                                                       NodeManager nodeManager,
                                                                       EthHandlerFactory ethHandlerFactory,
                                                                       StaticMessages staticMessages,
                                                                       PeerScoringManager peerScoringManager) {
        return () -> {
            HandshakeHandler handshakeHandler = new HandshakeHandler(config, peerScoringManager);
            MessageQueue messageQueue = new MessageQueue();
            P2pHandler p2pHandler = new P2pHandler(ethereumListener, configCapabilities, config);
            MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
            return new Channel(config, messageQueue, p2pHandler, messageCodec, handshakeHandler, nodeManager, ethHandlerFactory, staticMessages);
        };
    }

    @Bean
    public EthHandlerFactoryImpl.RskWireProtocolFactory getRskWireProtocolFactory(ApplicationContext ctx,
                                                                                  PeerScoringManager peerScoringManager,
                                                                                  Blockchain blockchain,
                                                                                  SystemProperties config,
                                                                                  CompositeEthereumListener ethereumListener){
        // FIXME break MessageHandler circular dependency
        return () -> new RskWireProtocol(peerScoringManager, ctx.getBean(MessageHandler.class), blockchain, config, ethereumListener);
    }

    @Bean
    public PeerServer getPeerServer(SystemProperties config,
                                    EthereumListener ethereumListener,
                                    EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return new PeerServerImpl(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public SyncConfiguration getSyncConfiguration() {
        int expectedPeers = RskSystemProperties.CONFIG.getExpectedPeers();
        int timeoutWaitingPeers = RskSystemProperties.CONFIG.getTimeoutWaitingPeers();
        int timeoutWaitingRequest = RskSystemProperties.CONFIG.getTimeoutWaitingRequest();
        int expirationTimePeerStatus = RskSystemProperties.CONFIG.getExpirationTimePeerStatus();
        int maxSkeletonChunks = RskSystemProperties.CONFIG.getMaxSkeletonChunks();
        int chunkSize = RskSystemProperties.CONFIG.getChunkSize();
        return new SyncConfiguration(expectedPeers, timeoutWaitingPeers, timeoutWaitingRequest,
                expirationTimePeerStatus, maxSkeletonChunks, chunkSize);
    }

    @Bean
    public BlockStore getBlockStore(){
        return new BlockStore();
    }
}
