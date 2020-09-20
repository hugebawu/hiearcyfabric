package cn.edu.ncepu.dataAggregate.application;

import cn.edu.ncepu.crypto.encryption.paillier.PaillierEngine;
import cn.edu.ncepu.crypto.encryption.paillier.PaillierProvider;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Base64;
import java.util.Collection;
import java.util.regex.Pattern;

public class PPDAGApp {
  private static final Logger logger = LoggerFactory.getLogger(PPDAGApp.class);
  private static final PaillierEngine engine = PaillierEngine.getInstance();
  private static final String DELIMITER = "[,]";
  private Cipher cipherHP = null;

  private User user;
  private HFClient client;
  private Channel channel;

  PublicKey pubKey = null;
  PrivateKey privKey = null;
  
  public void loadUser(String name,String mspId) throws Exception{
    String networkPath = this.getClass().getClassLoader().getResource("fabric").getFile()+"/network"; // 定位resources文件夹下的文件路径
    logger.debug(networkPath);
    File keystore = Paths.get(networkPath, "crypto-config","/peerOrganizations/org1.example.com", String.format("/users/%s@%s/msp/keystore", "Admin", "org1.example.com")).toFile();
    File keyFile = findFileSk(keystore); // 查找keystore路径下的以_sk结尾的文件
    if(null == keyFile ){
      throw new Exception("no secret key found");
    }
    String keyFileName = keystore.getPath() + "/" + keyFile.getName();

    File signCerts = Paths.get(networkPath, "crypto-config","/peerOrganizations/org1.example.com", String.format("/users/%s@%s/msp/signcerts", "Admin", "org1.example.com")).toFile();
    logger.debug(signCerts.getPath());
    File certFiles = findFileCert(signCerts);
    if(null == certFiles)
      throw new Exception("no signature cert found");
    String certFileName = signCerts.getPath() + "/" + certFiles.getName();
    
    this.user = new LocalUser(name,mspId,keyFileName,certFileName);
  }

  /**
   * 从指定路径中获取后缀为 _sk 的文件，且该路径下有且仅有该文件
   *
   * @param directory 指定路径
   * @return File
   */
  private File findFileSk(File directory) {
    File[] matches = directory.listFiles((dir, name) -> name.endsWith("_sk"));
    if (null == matches) {
      throw new RuntimeException(String.format("Matches returned null, does %s directory exist?", directory.getAbsoluteFile().getName()));
    }
    if (matches.length != 1) {
      throw new RuntimeException(String.format("Expected in %s only 1 sk file, but found %d", directory.getAbsoluteFile().getName(), matches.length));
    }
    return matches[0];
  }

  /**
   * 从指定路径中获取后缀为 .pem 的文件，且该路径下有且仅有该文件
   *
   * @param directory 指定路径
   * @return File
   */
  private File findFileCert(File directory) {
    File[] matches = directory.listFiles((dir, name) -> name.endsWith(".pem"));
    if (null == matches) {
      throw new RuntimeException(String.format("Matches returned null, does %s directory exist?", directory.getAbsoluteFile().getName()));
    }
    if (matches.length != 1) {
      throw new RuntimeException(String.format("Expected in %s only 1 pem file, but found %d", directory.getAbsoluteFile().getName(), matches.length));
    }
    return matches[0];
  }

  public void initChannel() throws Exception{
    if(this.user == null) throw new Exception("user not loaded");
    
    HFClient client = HFClient.createNewInstance();
    client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
    client.setUserContext(this.user);
    
    Channel channel = client.newChannel("ch1");
    Peer peer = client.newPeer("peer1","grpc://127.0.0.1:7051");
    channel.addPeer(peer);
    Orderer orderer = client.newOrderer("orderer1","grpc://127.0.0.1:7050");
    channel.addOrderer(orderer);

    channel.registerBlockListener(event -> {
      logger.info(String.format("the channel has generated the new block %d, which has %d transactions",event.getBlockNumber(),event.getTransactionCount()));
    });

    channel.registerChaincodeEventListener(Pattern.compile("mycc"),Pattern.compile("aggregate"),new ChaincodeEventListener(){
    @Override
    public void received(String handle,BlockEvent blockEvent,ChaincodeEvent chaincodeEvent) {
      try {
        logger.info(String.format("chaincode event payload: %s", new BigInteger(chaincodeEvent.getPayload())));
        BigInteger resultPlain = engine.decrypt(new BigInteger(chaincodeEvent.getPayload()).toByteArray(), privKey, cipherHP);
        logger.info("BigInteger resultPlain: " + resultPlain);
      } catch (Exception e) {
        e.printStackTrace();
        logger.error(e.getLocalizedMessage());
      }
    }});

    channel.initialize();
    
    this.channel = channel;
    this.client = client;
  }

  public void query(String ccname, String fcn, String...args) throws Exception{
    System.out.format("query %s %s...\n",ccname,fcn);
    QueryByChaincodeRequest req = this.client.newQueryProposalRequest();
    ChaincodeID cid = ChaincodeID.newBuilder().setName(ccname).build();
    req.setChaincodeID(cid);
    req.setFcn(fcn);
    req.setArgs(args);
    
    Collection<ProposalResponse> rspc = channel.queryByChaincode(req);
    
    for(ProposalResponse rsp: rspc){
      logger.info(String.format("status: %d \n",rsp.getStatus().getStatus()));
      logger.info(String.format("message: %s\n",rsp.getMessage()));
      String payLoad = rsp.getProposalResponse().getResponse().getPayload().toStringUtf8();
      logger.info(String.format("payload: %s\n", payLoad));
      /*BigInteger resultPlain = engine.decrypt(new BigInteger(payLoad).toByteArray(), privKey, cipherHP);
      logger.info("BigInteger resultPlain: " + resultPlain);*/
    }
  }
  
  public void invoke(String ccname,String fcn,String... args) throws Exception{
    System.out.format("invoke %s %s...\n",ccname,fcn);
    
    TransactionProposalRequest req = this.client.newTransactionProposalRequest();
    ChaincodeID cid = ChaincodeID.newBuilder().setName(ccname).build();    
    req.setChaincodeID(cid);
    req.setFcn(fcn);
    req.setArgs(args);
    
    Collection<ProposalResponse> rspc = channel.sendTransactionProposal(req);
    TransactionEvent event = channel.sendTransaction(rspc).get();

    logger.info(String.format("txid: %s\n", event.getTransactionID()));
    logger.info(String.format("valid: %b\n", event.isValid()));
  }
  
  public void start() throws Exception{
    loadUser("admin","Org1MSP");
    initChannel();

    /*String id = "12345";
    invoke("mycc","createAsset",id,"Tommy","a necklace");
    query("mycc","getAsset",id);
    invoke("mycc","transferAsset",id,"Mary");
    query("mycc","getAssetHistory",id);*/

    paillierInit();
    String numOrg1 = "100";
    String numOrg2 = "200";

    // get the n
    String[] keyComponents = pubKey.toString().split(DELIMITER);
    String keyComponent = "";
    for (String component : keyComponents) {
      if (component.startsWith("n")) {
        keyComponent = component.substring(2);// ignoring 'n:' or 'r:'
      }
    }
    BigInteger n = new BigInteger(keyComponent);
    BigInteger n2 = n.multiply(n);
    BigInteger first = new BigInteger(numOrg1);
    BigInteger second = new BigInteger(numOrg2);

    // encrypt
    BigInteger codedBytes1 = engine.encrypt(first.toByteArray(), pubKey, cipherHP);
    BigInteger codedBytes2 = engine.encrypt(second.toByteArray(), pubKey, cipherHP);

    String aggregator = "Org3";
    String base64numOrg1 =  new String (Base64.getEncoder().encode(codedBytes1.toByteArray()),"UTF-8");
    String base64numOrg2 =  new String (Base64.getEncoder().encode(codedBytes2.toByteArray()),"UTF-8");
    String base64N2 = new String (Base64.getEncoder().encode(n2.toByteArray()),"UTF-8");
    invoke("mycc","dataAggregation",aggregator,base64N2,base64numOrg1,base64numOrg2);
  }

  private void paillierInit() throws NoSuchAlgorithmException, NoSuchPaddingException {
    // Add dynamically the desired provider
    Security.addProvider(new PaillierProvider());
    /////////////////////////////////////////////////////////////////////
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("Paillier");
    kpg.initialize(32);
    KeyPair keyPair = kpg.generateKeyPair();
    this.pubKey = keyPair.getPublic();
    this.privKey = keyPair.getPrivate();
    cipherHP = Cipher.getInstance("PaillierHP");
  }
  public static void main(String[] args) throws Exception{
    logger.info("my privacy-preserving data aggregation dapp");
    new PPDAGApp().start();
  }
}