/*
 * DocDoku, Professional Open Source
 * Copyright 2006 - 2015 DocDoku SARL
 *
 * This file is part of DocDokuPLM.
 *
 * DocDokuPLM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DocDokuPLM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with DocDokuPLM.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.docdoku.api;

import com.docdoku.api.client.ApiException;
import com.docdoku.api.models.AccountDTO;
import com.docdoku.api.services.AccountsApi;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AccountsApiTest {

    @Test
    public void createAccountTest() throws ApiException {
        AccountDTO accountDTO = TestUtils.createAccount();
        AccountDTO account = new AccountsApi(new DocdokuPLMBasicClient(TestConfig.URL, accountDTO.getLogin(), TestConfig.PASSWORD).getClient()).getAccount();
        Assert.assertEquals(account.getLogin(), accountDTO.getLogin());
    }

    @Test
    public void getAccountTest() throws ApiException {
        AccountDTO account = new AccountsApi(TestConfig.REGULAR_USER_CLIENT).getAccount();
        Assert.assertEquals(account.getLogin(), TestConfig.LOGIN);
    }

    @Test
    public void updateAccountTest() throws ApiException {

        String newName = TestUtils.randomString();

        AccountsApi accountsApi = new AccountsApi(TestConfig.REGULAR_USER_CLIENT);
        AccountDTO account = accountsApi.getAccount();
        account.setName(newName);

        AccountDTO updatedAccount = accountsApi.updateAccount(account);
        Assert.assertEquals(updatedAccount.getName(), newName);
        Assert.assertEquals(updatedAccount,account);

    }

}
