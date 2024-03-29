#!/bin/bash

if [ ! -e configtx.yaml ];then
  echo "ERROR: configtx.yaml not found"
  exit 1
fi

ARTIFACT=artifact
if [ ! -d $ARTIFACT ];then
  mkdir $ARTIFACT
fi

#PROJECT=`dirname $PWD`
PROJECT=/root/Documents/idea-workspace/hiearcyfabric/PPDAG/src/main/java/cn/edu/ncepu/dataAggregate/
CHANNEL=ch1
CHAINCODE_NAME=mycc
CHAINCODE_VERSION=1.0
ORDERER=127.0.0.1:7050
PEER=127.0.0.1:7051
MSPDIR=./crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
MSPID=Org1MSP
  
init_channel(){
  configtxgen -outputCreateChannelTx ${ARTIFACT}/${CHANNEL}.tx -profile DefaultChannel -channelID ${CHANNEL} 

  CORE_PEER_MSPCONFIGPATH=${MSPDIR} \
  CORE_PEER_LOCALMSPID=${MSPID}  \
  CORE_PEER_ADDRESS=${PEER} \
  peer channel create -c ${CHANNEL} -f ${ARTIFACT}/${CHANNEL}.tx -o ${ORDERER} --outputBlock ${ARTIFACT}/${CHANNEL}.block
}

join_channel(){
  CORE_PEER_MSPCONFIGPATH=${MSPDIR} \
  CORE_PEER_LOCALMSPID=${MSPID}  \
  CORE_PEER_ADDRESS=${PEER} \
  peer channel join -b ${ARTIFACT}/${CHANNEL}.block  -o ${ORDERER} 
}

install_chaincode(){
  CORE_PEER_MSPCONFIGPATH=${MSPDIR} \
  CORE_PEER_LOCALMSPID=${MSPID}  \
  CORE_PEER_ADDRESS=${PEER} \
  peer chaincode install -n ${CHAINCODE_NAME} -v ${CHAINCODE_VERSION} -l java -p ${PROJECT}/chaincode
}

instantiate_chaincode(){
  CORE_PEER_MSPCONFIGPATH=${MSPDIR} \
  CORE_PEER_LOCALMSPID=${MSPID}  \
  CORE_PEER_ADDRESS=${PEER} \
  peer chaincode instantiate -n ${CHAINCODE_NAME} -v ${CHAINCODE_VERSION} -c '{"Args":[]}' -o ${ORDERER} -C ${CHANNEL}
}

init_channel
join_channel
install_chaincode
instantiate_chaincode
