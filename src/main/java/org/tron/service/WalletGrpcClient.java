package org.tron.service;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.api.WalletGrpc;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Contract.FreezeBalanceContract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.TransactionInfo;

public class WalletGrpcClient {

  private final ManagedChannel channel;
  private final WalletGrpc.WalletBlockingStub stub;

  public WalletGrpcClient(String host) {
    channel = ManagedChannelBuilder.forTarget(host)
        .usePlaintext(true)
        .build();
    stub = WalletGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public Transaction createTransaction(Contract.TransferContract contract) {
    return stub.createTransaction(contract);
  }

  public Transaction createTransaction(FreezeBalanceContract contract) {
    return stub.freezeBalance(contract);
  }

  public Account getAccount(Account account) {
    return stub.getAccount(account);
  }

  public boolean broadcastTransaction(Transaction signaturedTransaction) {
    GrpcAPI.Return response = stub.broadcastTransaction(signaturedTransaction);
    if (!response.getResult()) {
      String hash = Sha256Hash.of(signaturedTransaction.getRawData().toByteArray()).toString();
      System.err.println(
          "hash:" + hash + ",code:" + response.getCode() + ",msg:" + response.getMessage());
    }
    return response.getResult();
  }

  public TransactionInfo getTransactionInfoById(String txID) {
    ByteString bsTxId = ByteString.copyFrom(ByteArray.fromHexString(txID));
    BytesMessage request = BytesMessage.newBuilder().setValue(bsTxId).build();

    return stub.getTransactionInfoById(request);
  }

  public TransactionExtention createAssetIssue2(AssetIssueContract contract) {
    return stub.createAssetIssue2(contract);
  }
}
