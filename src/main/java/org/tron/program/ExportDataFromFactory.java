package org.tron.program;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import lombok.Getter;
import org.tron.Validator.LongValidator;
import org.tron.Validator.StringValidator;
import org.tron.common.crypto.Hash;
import org.tron.common.dispatch.TransactionFactory;
import org.tron.common.dispatch.creator.CreatorCounter;
import org.tron.common.config.Parameter.CommonConstant;
import org.tron.module.Account;
import org.tron.protos.Protocol.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

public class ExportDataFromFactory {

  private static List<Account> accounts = new ArrayList<>();

  private static Args argsObj;

  public static void main(String[] args) {
    long start = System.currentTimeMillis();

    ExportDataFromFactory exportDataFromFactory = new ExportDataFromFactory();

    exportDataFromFactory.initArgs(args);

    exportDataFromFactory.initNetType();

    exportDataFromFactory.initAccounts("accounts.txt");

    exportDataFromFactory.createTransactions();

    exportDataFromFactory.showInformation(start);

    System.exit(0);
  }

  private void initArgs(String[] args) {
    argsObj = new Args();
    JCommander.newBuilder().addObject(argsObj).build().parse(args);
  }

  private void initNetType() {
    if (CommonConstant.NET_TYPE_MAINNET.equals(argsObj.getNetType())) {
      Hash.changeAddressPrefixMainnet();
    } else {
      Hash.changeAddressPrefixTestnet();
    }
  }

  private void initAccounts(String file) {
    File ff = new File(file);
    FileInputStream fis = null;

    try {
      fis = new FileInputStream(ff);
      ObjectInputStream ois = new ObjectInputStream(fis);
      int count = 0;

      while (fis.available() > 0) {
        Account account = (Account) ois.readObject();
        accounts.add(account);

        if (((count + 1) % 100000) == 0) {
          System.out.println("read accounts current: " + (count + 1));
        }

        count++;
      }

    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private void createTransactions() {
    ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();

    AtomicLong counter = new AtomicLong(0);
    File f = new File(argsObj.getOutput());
    FileOutputStream fos;
    CountDownLatch countDownLatch = new CountDownLatch((int) argsObj.getCount());
    ExecutorService service = Executors.newFixedThreadPool(50);

    try {
      fos = new FileOutputStream(f);

      LongStream.range(0L, argsObj.getCount()).forEach(l -> {
        service.execute(() -> {
          long c = counter.incrementAndGet();
          if ((c + 1) % 1000 == 0) {
            System.out.println("create transactions current: " + (c + 1));
          }

          Optional.ofNullable(TransactionFactory.newTransaction()).ifPresent(transactions::add);
          countDownLatch.countDown();
        });
      });

      countDownLatch.await();
      counter.set(0L);

      for (Transaction transaction : transactions) {
        transaction.writeDelimitedTo(fos);
        long c = counter.incrementAndGet();

        if ((c + 1) % 1000 == 0) {
          System.out.println("write file current: " + (c + 1));
        }
      }

      fos.flush();
      fos.close();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void showInformation(long start) {
    System.out.println(
        "create " + argsObj.getCount() + " transactions need cost:" + ((System.currentTimeMillis() - start) / 1000)
            + "s");

    TransactionFactory.context.getBean(CreatorCounter.class).getCounterMap().entrySet().stream()
        .forEach((v) -> {
          System.out.println(v.getKey() + ": " + v.getValue().longValue());
        });
  }

  public static Args getArgsObj() {
    return argsObj;
  }

  public static List<Account> getAccounts() {
    return accounts;
  }

  public static class Args {

    @Getter
    @Parameter(names = {"--count",
        "-c"}, description = "Count", required = true, validateWith = LongValidator.class)
    private long count;

    @Getter
    @Parameter(names = {"--output",
        "-o"}, description = "Save data file", required = true, validateWith = StringValidator.class)
    private String output;

    @Getter
    @Parameter(names = {
        "--netType"}, description = "Net type", required = true, validateWith = StringValidator.class)
    private String netType;

    @Getter
    @Parameter(names = {
        "--context"}, description = "Application context", required = true, validateWith = StringValidator.class)
    private String context;
  }
}

