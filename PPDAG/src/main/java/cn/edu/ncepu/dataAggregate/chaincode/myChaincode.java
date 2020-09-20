package cn.edu.ncepu.dataAggregate.chaincode;

import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;

import com.google.protobuf.ByteString;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;


public class myChaincode extends ChaincodeBase {
  private static final Logger logger = LoggerFactory.getLogger(myChaincode.class);

  @Override
  public Response init(ChaincodeStub stub){
    System.out.println("--------init--------");

    JSONObject asset = new JSONObject();
    asset.put("aggregated",1);
    asset.put("affiliation","BJ");
    stub.putStringState("Org3",asset.toString());
    return newSuccessResponse(String.format("aggregator %s is initialized","Org3"));
  }
  
  @Override
  public Response invoke(ChaincodeStub stub){
    System.out.println("--------invoke--------");
    String fcn = stub.getFunction();
    List<String> args = stub.getParameters();
    System.out.println("===>" + fcn);
    switch(fcn){
      case "dataAggregation": return dataAggregation(stub,args);
    }
    return newErrorResponse("unimplemented method");
  }

  private Response dataAggregation(ChaincodeStub stub, List<String> args) {
    String aggregator = args.get(0);
    String base64N2 = args.get(1);

    // product
    BigInteger product  = new BigInteger("1");
    String cypherNumOrg1 = args.get(2);
    String cypherNumOrg2 = args.get(3);

    BigInteger n2 = null, codedBytes1, codedBytes2;
    try {
      n2 = new BigInteger(Base64.getDecoder().decode(base64N2.getBytes("UTF-8")));
      /*for ( String cypherNum : args.subList(2,args.size())) {
        product = product.multiply(new BigInteger(Base64.getDecoder().decode(cypherNum.getBytes("UTF-8"))));
      }*/
      codedBytes1 = new BigInteger(Base64.getDecoder().decode(cypherNumOrg1.getBytes("UTF-8")));
      codedBytes2 = new BigInteger(Base64.getDecoder().decode(cypherNumOrg2.getBytes("UTF-8")));
    }catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      logger.error(e.getLocalizedMessage());
      return newErrorResponse();
    }

    String aggregatorState = stub.getStringState(aggregator);
    if(aggregatorState == null || aggregatorState.length() == 0) {
      return newErrorResponse(String.format("aggregator %s not found",aggregator));
    }

    logger.info(String.format("the state of aggregator %s is %s",aggregator,aggregatorState));
    JSONObject asset = new JSONObject(aggregatorState);
    BigInteger aggregated =  asset.getBigInteger("aggregated");

    product = codedBytes1.multiply(codedBytes2);

    // product mod n^2
    BigInteger tallyProduct = product.mod(n2);
    logger.info(" Product mod n^2: " + tallyProduct);

    asset.put("aggregated",tallyProduct);
    logger.info(String.format("the new state of aggregator %s is %s",aggregator,asset.toString()));
    stub.putStringState(aggregator,asset.toString());

    // motivate a chaincode event
    stub.setEvent("aggregate",tallyProduct.toByteArray());
    return newSuccessResponse(String.format("aggregated num of %s updated",aggregator),ByteString.copyFromUtf8(tallyProduct.toString()).toByteArray());
  }

  private byte[] toBytes(String str){
    return ByteString.copyFromUtf8(str).toByteArray();
  }

  public static void main(String[] args){
    if (null != args && args.length > 0){
      for (int i = 0; i < args.length; i++) {
        System.out.println(args[i]);
      }
    }else {
      args = new String[4];
      args[0] = "--id";
      args[1] = "mycc:1.0";
      args[2] = "--peer.address";
      args[3] = "127.0.0.1:7052";
    }
    logger.info("mycc");
    new myChaincode().start(args);
  }
}