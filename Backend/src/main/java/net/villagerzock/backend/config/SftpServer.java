package net.villagerzock.backend.config;

import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.service.AuthService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import net.villagerzock.backend.sftp.CloudCoreFileSystem;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.BuiltinUserAuthFactories;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;


@Configuration
public class SftpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SftpServer.class);

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshServer cloudCoreSftpServer(
            AuthService service,
            CloudCoreNodeRepository nodes,
            NodeHandshakeClient handshakeClient,
            @Value("${cloudcore.sftp-port:2222}") int port
    ) {
        SshServer server = SshServer.setUpDefaultServer();

        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of("hostkey.ser")));

        server.setPasswordAuthenticator((username, password, session) -> {
            boolean result = service.checkPassword(username, password);
            if (!result) {
                LOGGER.warn("Wrong credentials for user '{}'", username);
            }
            return result;
        });
        server.setPublickeyAuthenticator((username, key, session) -> {
            boolean result = service.checkSshKey(username, key);
            if (result) {
                LOGGER.info("Accepted public key authentication for user '{}' with {} key", username, key.getAlgorithm());
            } else {
                LOGGER.warn("Rejected public key authentication for user '{}' with {} key", username, key.getAlgorithm());
            }
            return result;
        });
        server.setUserAuthFactories(BuiltinUserAuthFactories
                .parseFactoriesList("publickey", "password")
                .getParsedFactories());
        LOGGER.info("CloudCore SFTP enabled on port {} with SSH auth methods: publickey,password", port);

        server.setSubsystemFactories(List.of(
                new SftpSubsystemFactory.Builder()
                        .build()
        ));

        server.setFileSystemFactory(new FileSystemFactory() {
            @Override
            public Path getUserHomeDir(SessionContext session) throws IOException {
                return null;
            }

            @Override
            public FileSystem createFileSystem(SessionContext session) throws IOException {
                UserAccount user = service.findByUsername(session.getUsername())
                        .orElseThrow(() -> new IllegalStateException("Logged in user does not exist"));
                return new CloudCoreFileSystem(user, nodes, handshakeClient);
            }
        });

        return server;
    }
}
