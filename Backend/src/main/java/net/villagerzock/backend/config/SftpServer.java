package net.villagerzock.backend.config;

import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.service.AuthService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import net.villagerzock.backend.sftp.CloudCoreFileSystem;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class SftpServer {
    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshServer cloudCoreSftpServer(
            AuthService service,
            CloudCoreNodeRepository nodes,
            NodeHandshakeClient handshakeClient,
            @Value("${cloudcore.sftp-port:2222}") int port
    ){
        SshServer server = SshServer.setUpDefaultServer();

        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of("hostkey.ser")));

        server.setPasswordAuthenticator((username, password, session) ->{
            boolean result = service.checkPassword(username, password);
            if (!result) {
                System.out.println("Wrong Credentials");
            }
            return result;
        });

        server.setSubsystemFactories(List.of(
                new SftpSubsystemFactory.Builder().build()
        ));

        server.setFileSystemFactory(new FileSystemFactory() {
            @Override
            public Path getUserHomeDir(SessionContext session) throws IOException {
                return null;
            }

            @Override
            public FileSystem createFileSystem(SessionContext session) throws IOException {
                UserAccount user = service.findByUsername(session.getUsername()).orElseThrow(()->new IllegalStateException("Logged in User not existent"));
                return new CloudCoreFileSystem(user, nodes, handshakeClient);
            }
        });

        return server;
    }
}
