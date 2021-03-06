/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.jsontestsuite.validators;

import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.ethereum.util.ByteUtil.difference;

public class RepositoryValidator {

    public static List<String> valid(Repository currentRepository, Repository postRepository,boolean validateRootHash) {

        List<String> results = new ArrayList<>();

        Set<ByteArrayWrapper> currentKeys = currentRepository.getAccountsKeys();
        Set<ByteArrayWrapper> expectedKeys = postRepository.getAccountsKeys();

        if (expectedKeys.size() != currentKeys.size()) {

            String out =
                    String.format("The size of the repository is invalid \n expected: %d, \n current: %d",
                            expectedKeys.size(), currentKeys.size());
            results.add(out);
        }

        for (ByteArrayWrapper wrappedAddress : currentKeys) {
            byte[] address = wrappedAddress.getData();

            AccountState state = currentRepository.getAccountState(address);
            ContractDetails details = currentRepository.getContractDetails(address);

            AccountState postState = postRepository.getAccountState(address);
            ContractDetails postDetails = postRepository.getContractDetails(address);

            List<String> accountResult =
                AccountValidator.valid(Hex.toHexString(address), postState, postDetails, state, details);

            results.addAll(accountResult);
        }

        Set<ByteArrayWrapper> expectedButAbsent = difference(expectedKeys, currentKeys);
        for (ByteArrayWrapper address : expectedButAbsent){
            String formattedString = String.format("Account: %s: expected but doesn't exist",
                    Hex.toHexString(address.getData()));
            results.add(formattedString);
        }

        // Compare roots
        String postRoot = Hex.toHexString(postRepository.getRoot());
        String currRoot = Hex.toHexString(currentRepository.getRoot());

        if (validateRootHash)
        if (!postRoot.equals(currRoot)){

            String formattedString = String.format("Root hash doesn't match: expected: %s current: %s",
                    postRoot, currRoot);
            results.add(formattedString);
        }

        return results;
    }

}
