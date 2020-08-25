# Private data asset transfer scenario

The private data asset transfer smart contract uses a simple asset transfer to demonstrate the use of private data collections. All the data that is created by the smart contract is stored in the private data that are collections specified in the `collections_config.json` file:

- The `assetCollection` is deployed on the peers of Org1 and Org2. This collection is used to store the main asset details, such as the size, color, and owner. The `"memberOnlyRead"` and `"memberOnlyWrite"` parameters are used to specify that only Org1 and Org2 can read and write to this collection.
- The `Org1MSPPrivateCollection` is deployed only on the Org1 peer. Similarly, the `Org2MSPPrivateCollection` is only deployed on the Org2 peer. These organization specific collections are used to store the appraisal value of the asset. This allows the owner of the asset to keep the value of the asset private from other organizations on the channel. The `"endorsementPolicy"` parameter is used to create a collection specific endorsement policy. Each update to `Org1MSPPrivateCollection` or `Org2MSPPrivateCollection` needs to be endorsed by the organization that stores the collection on their peers.

These three collections are used to transfer the asset between Org1 and Org2. In the tutorial, you will use the private data smart contract to complete the following transfer scenario:

- A member of Org1 uses the `CreateAsset` function to create a new asset. The `CreateAsset` function reads the certificate information of the client identity that submitted the transaction using the `GetClientIdentity.GetID()` API and creates a new asset with the client identity as the asset owner. The main details of the asset, including the owner, are stored in the `assetCollection` collection. The asset is also created with an appraised value. The appraised value is used by each participant to agree to the transfer of the asset, and is only stored in each organization specific collection. Because the asset is created by a member of Org1, the initial appraisal value agreed by the asset owner is stored in the `Org1MSPPrivateCollection`.
- A member of Org2 creates an agreement to trade using the `AgreeToTransfer` function. The potential buyer uses this function to agree to an appraisal value. The value is stored in `Org2MSPPrivateCollection`, and can only read by a member of Org2. The `AgreeToTransfer` function also uses the `GetClientIdentity().GetID()` API to read the client identity that is agreeing to the Transfer. The **TransferAgreement** is stored in the `assetCollection` as a key with the name "transferAgreement{assetID}"
- After the member of Org2 has agreed to the transfer, the asset owner can transfer the asset to the buyer using the `TransferAsset` function. The smart contract completes a couple of checks before the asset is transferred:
    - The transfer request is submitted by the owner of the asset.
    - The smart contract uses the `GetPrivateDataHash()` function to check that the hash of the asset appraisal value in `Org1MSPPrivateCollection` matches the hash of the appraisal value in the `Org2MSPPrivateCollection`. If the hashes are the same, it confirms that the owner and the interested buyer have agreed to the same asset value.
  If both conditions are met, the transfer function will get the client ID of the buyer from the transfer agreement and make the buyer the new owner of the asset. The transfer function will also delete the asset appraisal value from the collection of the former owner, as well as remove the transfer agreement from the `assetCollection`.

The private data asset transfer enabled by this smart contract is meant to demonstrate the use private data collections. For an example of a more realistic transfer scenario, see the [secure asset transfer smart contract](../../asset-transfer-secured-agreement/chaincode-go).

## Deploy the smart contract to the test network

You can run the private data transfer scenario using the Fabric test network. Open a command terminal and navigate to test network directory in your local clone of the `fabric-samples`. We will operate from the `test-network` directory for the remainder of the tutorial.
```
cd fabric-samples/test-network
```

Run the following command to deploy the test network:

```
./network.sh up createChannel -ca -s couchdb
```

The test network is deployed with two peer organizations. The `createChannel` flag deploys the network with a single channel named `mychannel` with Org1 and Org2 as channel members. The `-ca` flag is used to deploy the network using certificate authorities. This allows you to use each organization's CA to register and enroll new users for this tutorial.

## Deploy the smart contract to the channel

You can use the test network script to deploy the private data smart contract to the channel that was just created. Deploy the smart contract to `mychannel` using the following command:
```
./network.sh deployCC -ccn private -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-go/collections_config.json
```

The above command deploys the go chaincode with short name `private`, and specifies the private data collection configuration from file `collections_config.json` using `-cccg` flag.
Note that we are using the `-ccep` flag to deploy the private data smart contract with a chaincode endorsement policy of `"OR('Org1MSP.peer','Org2MSP.peer')"`. This allows Org1 and Org2 to create an asset without receiving an endorsement from the other organization. 

Now you are ready to call the deployed smart contract.
Note that this sample workflow steps below, can also be executed via the application at `asset-transfer-private-data/application-javascript` folder, in fewer steps. To execute the workflow via CLI, read on.

## Register identities

The private data transfer smart contract supports ownership by individual identities that belong to the network. In our scenario, the owner of the asset will be a member of Org1, while the buyer will belong to Org2. To highlight the connection between the `GetClientIdentity().GetID()` API and the information within a user's certificate, we will register two new identities using the Org1 and Org2 Certificate Authorities (CA's), and then use the CA's to generate each identity's certificate and private key.

First, we need to set the following environment variables to use the the Fabric CA client:
```
export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=$PWD/../config/
```

We will use the Org1 CA to create the identity asset owner. Set the Fabric CA client home to the MSP of the Org1 CA admin (this identity was generated by the test network script):
```
export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org1.example.com/
```

You can register a new owner client identity using the `fabric-ca-client` tool:
```
fabric-ca-client register --caname ca-org1 --id.name owner --id.secret ownerpw --id.type client --tls.certfiles ${PWD}/organizations/fabric-ca/org1/tls-cert.pem
```

You can now generate the identity certificates and MSP folder by providing the enroll name and secret to the enroll command:
```
fabric-ca-client enroll -u https://owner:ownerpw@localhost:7054 --caname ca-org1 -M ${PWD}/organizations/peerOrganizations/org1.example.com/users/owner@org1.example.com/msp --tls.certfiles ${PWD}/organizations/fabric-ca/org1/tls-cert.pem
```

Run the command below to copy the Node OU configuration file into the owner identity MSP folder.
```
cp ${PWD}/organizations/peerOrganizations/org1.example.com/msp/config.yaml ${PWD}/organizations/peerOrganizations/org1.example.com/users/owner@org1.example.com/msp/config.yaml
```

We can now use the Org2 CA to create the buyer identity. Set the Fabric CA client home the Org2 CA admin:
```
export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org2.example.com/
```

You can register a new owner client identity using the `fabric-ca-client` tool:
```
fabric-ca-client register --caname ca-org2 --id.name buyer --id.secret buyerpw --id.type client --tls.certfiles ${PWD}/organizations/fabric-ca/org2/tls-cert.pem
```

We can now enroll to generate the identity MSP folder:
```
fabric-ca-client enroll -u https://buyer:buyerpw@localhost:8054 --caname ca-org2 -M ${PWD}/organizations/peerOrganizations/org2.example.com/users/buyer@org2.example.com/msp --tls.certfiles ${PWD}/organizations/fabric-ca/org2/tls-cert.pem
```

Run the command below to copy the Node OU configuration file into the buyer identity MSP folder.
```
cp ${PWD}/organizations/peerOrganizations/org2.example.com/msp/config.yaml ${PWD}/organizations/peerOrganizations/org2.example.com/users/buyer@org2.example.com/msp/config.yaml
```

## Create an asset

Now that we have created the identity of the asset owner, we can invoke the private data smart contract to create a new asset. Use the following environment variables to operate the `peer` CLI as the owner identity from Org1.

```
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/owner@org1.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:7051
```

Run the following command to define the asset properties:
```
export ASSET_PROPERTIES=$(echo -n "{\"objectType\":\"asset\",\"assetID\":\"asset1\",\"color\":\"green\",\"size\":20,\"appraisedValue\":100}" | base64 | tr -d \\n)
```

We can then invoke the smart contract to create the new asset:
```
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"CreateAsset","Args":[]}' --transient "{\"asset_properties\":\"$ASSET_PROPERTIES\"}"
```

The command above uses the transient data flag, `--transient`, to provide the asset details to the smart contract. Transient data is not part of the transaction read/write set, and as result is not stored on the channel ledger.

Note that command above only targets the Org1 peer. The `CreateAsset` transactions writes to two collections, `assetCollection` and `Org1MSPPrivateCollection`. The `Org1MSPPrivateCollection` requires and endorsement from the Org1 peer in order to write to the collection, while the `assetCollection` inherits the endorsement policy of the chaincode, `"OR('Org1MSP.peer','Org2MSP.peer')"`. An endorsement from the Org1 peer can meet both endorsement policies and is able to create an asset without an endorsement from Org2.

We can read the main details of the asset that was created by using the `ReadAsset` function to query the `assetCollection` collection:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAsset","Args":["asset1"]}'
```

When successful, the command will return the following result:
```
{"objectType":"asset","assetID":"asset1","color":"green","size":20,"owner":"eDUwOTo6Q049b3duZXIsT1U9Y2xpZW50LE89SHlwZXJsZWRnZXIsU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUzo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="}
```

The `"owner"` of the asset is the identity that created the asset by invoking the smart contract. The `GetClientIdentity().GetID()` API reads the common name and issuer of the identity certificate. You can see that information by decoding the owner string out of base64 format:
```
echo eDUwOTo6Q049b3duZXIsT1U9Y2xpZW50LE89SHlwZXJsZWRnZXIsU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUzo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw | base64 --decode
```

The result will show the common name and issuer of the owner certificate:
```
x509::CN=owner,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=Umacbook-air:test-network
```

A member of Org1 can also read the private asset appraisal value that is stored in the `Org1MSPPrivateCollection` on the Org1 peer:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAssetPrivateDetails","Args":["Org1MSPPrivateCollection","asset1"]}'
```
The query will return the value of the asset:
```
{"assetID":"asset1","appraisedValue":100}
```

### Buyer from Org2 agrees to buy the asset

The buyer identity from Org2 is interested in buying the asset. In a new terminal, set the following environment variables to operate as the buyer:

```
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org2.example.com/users/buyer@org2.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:9051
```

Now that we are operating as a member of Org2, we can demonstrate that the asset appraisal is not stored in Org2MSPPrivateCollection, on the Org2 peer:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAssetPrivateDetails","Args":["Org2MSPPrivateCollection","asset1"]}'
```
The empty response shows that, the asset1 private details, does not exist in buyer private collection.

Nor can a member of Org2, able to read the Org1 private data collection:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAssetPrivateDetails","Args":["Org1MSPPrivateCollection","asset1"]}'
```
By setting `"memberOnlyRead": true` in the collection configuration file, we specify that only members of Org1 can read data from the collection. A Org2 member who tries to read the collection would only get the following response.
```
Error: endorsement failure during query. response: status:500 message:"failed to read from asset details GET_STATE failed: transaction ID: 10d39a7d0b340455a19ca4198146702d68d884d41a0e60936f1599c1ddb9c99d: tx creator does not have read access permission on privatedata in chaincodeName:private collectionName: Org1MSPPrivateCollection"
```

To purchase the asset, the buyer needs to agree to the same value as the asset owner. The agreed value will be stored in the `Org2MSPDetailsCollection` collection on the Org2 peer. Run the following command to agree to the appraised value of 100:
```
export ASSET_VALUE=$(echo -n "{\"assetID\":\"asset1\",\"appraisedValue\":100}" | base64 | tr -d \\n)
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"AgreeToTransfer","Args":[]}' --transient "{\"asset_value\":\"$ASSET_VALUE\"}"
```

The buyer can now query the value they agreed to in the Org2 private data collection:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAssetPrivateDetails","Args":["Org2MSPPrivateCollection","asset1"]}'
```
The invoke will return the following value:
```
{"assetID":"asset1","appraisedValue":100}
```

## Org1 member transfers the asset to Org2

Now that buyer has agreed to buy the asset for appraised value, the owner from Org1 can transfer the asset to Org2. In the first terminal (with the following environment variables to operate as Org1):
```
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/owner@org1.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:7051
```

Now that buyer has agreed to buy the asset for appraised value, the owner from Org1 can read the data added by `AgreeToTransfer` to see buyer identity.
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadTransferAgreement","Args":["asset1"]}'
```

The owner from Org1 can now transfer the asset to Org2. To transfer the asset, the owner needs to pass the MSP ID of new asset owner Org. The transfer function will read the client ID of the interested buyer user from the transfer agreement.
```
export ASSET_OWNER=$(echo -n "{\"assetID\":\"asset1\",\"buyerMSP\":\"Org2MSP\"}" | base64 | tr -d \\n)
```

The owner of the asset needs to initiate the transfer.

```
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"TransferAsset","Args":[]}' --transient "{\"asset_owner\":\"$ASSET_OWNER\"}" --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
```
You can ReadAsset `asset1` to see the results of the transfer.
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAsset","Args":["asset1"]}'
```

The results will show that the buyer identity now owns the asset:

```
{"objectType":"asset","assetID":"asset1","color":"green","size":20,"owner":"eDUwOTo6Q049YnV5ZXIsT1U9Y2xpZW50LE89SHlwZXJsZWRnZXIsU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUzo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"}
```

You can base64 decode the `"owner"` to see that it is the buyer identity:
```
x509::CN=buyer,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UKmacbook-air
```

You can also confirm that transfer removed the private details from the Org1 collection:
```
peer chaincode query -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n private -c '{"function":"ReadAssetPrivateDetails","Args":["Org1MSPPrivateCollection","asset1"]}'
```
Your query will return empty result, since the asset private data is removed from the Org1 private data collection.

## Clean up

When you are finished, you can bring down the test network. The command will remove all the nodes of the test network, and delete any ledger data that you created:

```
./network.sh down
```
