/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.hyperledger.fabric.client.CallOption;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

public final class Connections {
    public static final String CHANNEL_NAME = Utils.getEnvOrDefault("CHANNEL_NAME", "mychannel");
    public static final String CHAINCODE_NAME = Utils.getEnvOrDefault("CHAINCODE_NAME", "basic");

    private static final String PEER_NAME = "peer0.org1.example.com";
    private static final String MSP_ID = Utils.getEnvOrDefault("MSP_ID", "Org1MSP");

    // Path to crypto materials.
    private static final Path CRYPTO_PATH = Utils.getEnvOrDefault(
            "CRYPTO_PATH",
            Paths::get,
            Paths.get("..", "..", "..", "test-network", "organizations", "peerOrganizations", "org1.example.com")
    );

    // Path to user private key directory.
    private static final Path KEY_DIR_PATH = Utils.getEnvOrDefault(
            "KEY_DIRECTORY_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.example.com", "msp", "keystore"))
    );

    // Path to user certificate.
    private static final Path CERT_PATH = Utils.getEnvOrDefault(
            "CERT_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.example.com", "msp", "signcerts", "cert.pem"))
    );

    // Path to peer tls certificate.
    private static final Path TLS_CERT_PATH = Utils.getEnvOrDefault(
            "TLS_CERT_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("peers", PEER_NAME, "tls", "ca.crt"))
    );

    // Gateway peer end point.
    private static final String PEER_ENDPOINT = Utils.getEnvOrDefault("PEER_ENDPOINT", "localhost:7051");

    // Gateway peer SSL host name override.
    private static final String PEER_HOST_ALIAS = Utils.getEnvOrDefault("PEER_HOST_ALIAS", PEER_NAME);

    private static final long EVALUATE_TIMEOUT_SECONDS = 5;
    private static final long ENDORSE_TIMEOUT_SECONDS = 15;
    private static final long SUBMIT_TIMEOUT_SECONDS = 5;
    private static final long COMMIT_STATUS_TIMEOUT_SECONDS = 60;

    private Connections() {
        // Private constructor to prevent instantiation
    }

    public static ManagedChannel newGrpcConnection() throws IOException, CertificateException {
        Reader tlsCertReader = Files.newBufferedReader(TLS_CERT_PATH);
        X509Certificate tlsCert = Identities.readX509Certificate(tlsCertReader);

        return NettyChannelBuilder.forTarget(PEER_ENDPOINT)
                .sslContext(GrpcSslContexts.forClient().trustManager(tlsCert).build()).overrideAuthority(PEER_HOST_ALIAS)
                .build();
    }

    public static Gateway.Builder newGatewayBuilder(final Channel grpcChannel) throws CertificateException, IOException, InvalidKeyException {
        return Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .connection(grpcChannel)
                .evaluateOptions(CallOption.deadlineAfter(EVALUATE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .endorseOptions(CallOption.deadlineAfter(ENDORSE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .submitOptions(CallOption.deadlineAfter(SUBMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .commitStatusOptions(CallOption.deadlineAfter(COMMIT_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static Identity newIdentity() throws IOException, CertificateException {
        Reader certReader = Files.newBufferedReader(CERT_PATH);
        X509Certificate certificate = Identities.readX509Certificate(certReader);

        return new X509Identity(MSP_ID, certificate);
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        Reader keyReader = Files.newBufferedReader(getPrivateKeyPath());
        PrivateKey privateKey = Identities.readPrivateKey(keyReader);

        return Signers.newPrivateKeySigner(privateKey);
    }

    private static Path getPrivateKeyPath() throws IOException {
        try (Stream<Path> keyFiles = Files.list(KEY_DIR_PATH)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }
}
