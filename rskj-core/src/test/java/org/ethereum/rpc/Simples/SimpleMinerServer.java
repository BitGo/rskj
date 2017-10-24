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

package org.ethereum.rpc.Simples;

import co.rsk.mine.MinerServer;
import co.rsk.mine.MinerWork;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.mine.SubmitBlockResult;
import co.rsk.mine.SubmittedBlockInfo;
import org.spongycastle.util.encoders.Hex;

/**
 * Created by Ruben on 22/06/2016.
 */
public class SimpleMinerServer implements MinerServer {

    public String coinbase;

    @Override
    public void start() {}

    @Override
    public SubmitBlockResult submitBitcoinBlock(String blockHashForMergedMining, BtcBlock bitcoinMergedMiningBlock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getCoinbaseAddress() {
        return Hex.decode(coinbase);
    }

    @Override
    public MinerWork getWork() {return null;}

    @Override
    public void buildBlockToMine(org.ethereum.core.Block b, boolean createCompetitiveBlock) {}

    @Override
    public long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public long increaseTime(long seconds) {
        throw new UnsupportedOperationException();
    }
}
