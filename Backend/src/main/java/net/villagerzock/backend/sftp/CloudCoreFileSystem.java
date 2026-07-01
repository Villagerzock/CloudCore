package net.villagerzock.backend.sftp;

import net.villagerzock.backend.dto.FileSystemDelegatorResponse;
import net.villagerzock.backend.dto.FolderResponse;
import net.villagerzock.backend.dto.SaveTemplateFileRequest;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.dto.TemplatePathRequest;
import net.villagerzock.backend.dto.UploadTemplateFileRequest;
import net.villagerzock.backend.entity.CloudCoreNode;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.service.NodeHandshakeClient;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class CloudCoreFileSystem extends FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudCoreFileSystem.class);

    private final UserAccount user;
    private final CloudCoreNodeRepository nodes;
    private final NodeHandshakeClient handshakeClient;
    private final CloudCoreFileSystemProvider provider;
    private boolean open = true;

    public CloudCoreFileSystem(
            UserAccount user,
            CloudCoreNodeRepository nodes,
            NodeHandshakeClient handshakeClient
    ) {
        this.user = user;
        this.nodes = nodes;
        this.handshakeClient = handshakeClient;
        this.provider = new CloudCoreFileSystemProvider(this);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        ensureOpen();
        return List.of(new CloudCorePath(this, List.of(), true));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        ensureOpen();
        return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(String first, String... more) {
        ensureOpen();
        StringBuilder value = new StringBuilder(first == null ? "" : first);
        for (String part : more) {
            if (!value.isEmpty() && value.charAt(value.length() - 1) != '/') {
                value.append('/');
            }
            value.append(part);
        }
        return CloudCorePath.parse(this, value.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        ensureOpen();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        return path -> matcher.matches(Path.of(path.toString()));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("User principals are not supported");
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("Watch service is not supported");
    }

    private void ensureOpen() {
        if (!open) {
            throw new ClosedFileSystemException();
        }
    }

    private List<CloudCoreNode> accessibleNodes() {
        return nodes.findLinkedByUsername(user.getUsername());
    }

    private Optional<CloudCoreNode> node(String segment) {
        return accessibleNodes().stream()
                .filter(node -> segment.equals(node.getName()) || segment.equals(String.valueOf(node.getId())))
                .findFirst();
    }

    private List<Path> list(CloudCorePath path) throws IOException {
        try {
            ResolvedPath resolved = resolve(path);
            return switch (resolved.kind()) {
                case ROOT -> accessibleNodes().stream()
                        .map(node -> (Path)path.resolve(node.getName()))
                        .toList();
                case NODE -> List.of(path.resolve("templates"));
                case TEMPLATES -> handshakeClient.getTemplates(resolved.node().getId()).stream()
                        .map(ServerTemplateDto::name)
                        .map(other -> (Path) path.resolve(other))
                        .toList();
                case TEMPLATE, TEMPLATE_PATH -> listTemplatePath(path, resolved);
                case TEMPLATE_FILE -> throw new NotDirectoryException(path.toString());
                case MISSING -> throw new NoSuchFileException(path.toString());
            };
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to list path '{}'", path, exception);
            throw exception;
        }
    }

    private List<Path> listTemplatePath(CloudCorePath path, ResolvedPath resolved) throws IOException {
        try {
            FileSystemDelegatorResponse response = templateResponse(resolved);
            if (response.isFile()) {
                throw new NotDirectoryException(path.toString());
            }
            List<FolderResponse.FileInFolder> files = response.files() == null ? List.of() : response.files();
            return files.stream()
                    .map(FolderResponse.FileInFolder::name)
                    .map(other -> (Path) path.resolve(other))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to list template path '{}'", path, exception);
            throw exception;
        }
    }

    private byte[] read(CloudCorePath path) throws IOException {
        try {
            ResolvedPath resolved = resolve(path);
            if (resolved.kind() != PathKind.TEMPLATE_FILE) {
                throw new NoSuchFileException(path.toString());
            }
            ResponseEntity<byte[]> response = handshakeClient.getTemplateFileContent(
                    resolved.node().getId(),
                    resolved.template(),
                    resolved.templatePath());
            return response.getBody() == null ? new byte[0] : response.getBody();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to read file '{}'", path, exception);
            throw exception;
        }
    }

    private CloudCoreAttributes attributes(CloudCorePath path) throws IOException {
        try {
            ResolvedPath resolved = resolve(path);
            return switch (resolved.kind()) {
                case ROOT, NODE, TEMPLATES, TEMPLATE, TEMPLATE_PATH -> {
                    if (resolved.kind() == PathKind.TEMPLATE_PATH) {
                        FileSystemDelegatorResponse response = templateResponse(resolved);
                        if (response.isFile()) {
                            yield CloudCoreAttributes.file(response.sizeBytes() == null ? 0 : response.sizeBytes());
                        }
                    }
                    yield CloudCoreAttributes.directory();
                }
                case TEMPLATE_FILE -> {
                    FileSystemDelegatorResponse response = templateResponse(resolved);
                    yield CloudCoreAttributes.file(response.sizeBytes() == null ? 0 : response.sizeBytes());
                }
                case MISSING -> throw new NoSuchFileException(path.toString());
            };
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to read attributes for '{}'", path, exception);
            throw exception;
        }
    }

    private ResolvedPath resolve(CloudCorePath path) {
        List<String> parts = path.parts();
        if (parts.isEmpty()) {
            return ResolvedPath.root();
        }

        Optional<CloudCoreNode> node = node(parts.get(0));
        if (node.isEmpty()) {
            return ResolvedPath.missing();
        }
        if (parts.size() == 1) {
            return ResolvedPath.node(node.get());
        }
        if (!"templates".equals(parts.get(1))) {
            return ResolvedPath.missing();
        }
        if (parts.size() == 2) {
            return ResolvedPath.templates(node.get());
        }
        String template = parts.get(2);
        boolean templateExists = handshakeClient.getTemplates(node.get().getId()).stream()
                .anyMatch(candidate -> candidate.name().equals(template));
        if (!templateExists) {
            return ResolvedPath.missing();
        }
        if (parts.size() == 3) {
            return ResolvedPath.template(node.get(), template);
        }

        String templatePath = String.join("/", parts.subList(3, parts.size()));
        try {
            FileSystemDelegatorResponse response = handshakeClient.getTemplateFileSystemPath(
                    node.get().getId(),
                    template,
                    templatePath);
            return response.isFile()
                    ? ResolvedPath.templateFile(node.get(), template, templatePath)
                    : ResolvedPath.templatePath(node.get(), template, templatePath);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to resolve template path '{}' in template '{}' on node '{}'", templatePath, template, node.get().getName(), exception);
            return ResolvedPath.missing();
        }
    }

    private FileSystemDelegatorResponse templateResponse(ResolvedPath resolved) {
        return handshakeClient.getTemplateFileSystemPath(
                resolved.node().getId(),
                resolved.template(),
                resolved.templatePath() == null ? "" : resolved.templatePath());
    }

    private void createDirectory(CloudCorePath path) throws IOException {
        TemplateTarget target = resolveTemplateTarget(path);
        if (target.templatePath().isBlank()) {
            throw new FileAlreadyExistsException(path.toString());
        }
        if (resolve(path).kind() != PathKind.MISSING) {
            throw new FileAlreadyExistsException(path.toString());
        }
        handshakeClient.createTemplateFolder(
                target.node().getId(),
                target.template(),
                target.parentPath(),
                new TemplatePathRequest(target.fileName()));
    }

    private void delete(CloudCorePath path) throws IOException {
        ResolvedPath resolved = resolve(path);
        if (resolved.kind() != PathKind.TEMPLATE_PATH && resolved.kind() != PathKind.TEMPLATE_FILE) {
            throw new NoSuchFileException(path.toString());
        }
        handshakeClient.deleteTemplatePath(resolved.node().getId(), resolved.template(), resolved.templatePath());
    }

    private void copy(CloudCorePath sourcePath, CloudCorePath targetPath, boolean replaceExisting) throws IOException {
        ResolvedPath source = requireExistingTemplatePath(sourcePath);
        TemplateTarget target = resolveTemplateTarget(targetPath);
        requireSameTemplate(source, target, sourcePath, targetPath);
        ResolvedPath existingTarget = resolve(targetPath);
        if (existingTarget.kind() != PathKind.MISSING) {
            if (!replaceExisting) {
                throw new FileAlreadyExistsException(targetPath.toString());
            }
            delete(targetPath);
        }

        handshakeClient.copyTemplatePath(
                source.node().getId(),
                source.template(),
                source.templatePath(),
                new TemplatePathRequest(target.parentPath()));
        renameCopiedOrMovedPathIfNeeded(source, target);
    }

    private void move(CloudCorePath sourcePath, CloudCorePath targetPath, boolean replaceExisting) throws IOException {
        ResolvedPath source = requireExistingTemplatePath(sourcePath);
        TemplateTarget target = resolveTemplateTarget(targetPath);
        requireSameTemplate(source, target, sourcePath, targetPath);
        if (source.templatePath().equals(target.templatePath())) {
            return;
        }
        ResolvedPath existingTarget = resolve(targetPath);
        if (existingTarget.kind() != PathKind.MISSING) {
            if (!replaceExisting) {
                throw new FileAlreadyExistsException(targetPath.toString());
            }
            delete(targetPath);
        }

        if (source.parentPath().equals(target.parentPath())) {
            if (!source.fileName().equals(target.fileName())) {
                handshakeClient.renameTemplatePath(
                        source.node().getId(),
                        source.template(),
                        source.templatePath(),
                        new TemplatePathRequest(target.fileName()));
            }
            return;
        }

        handshakeClient.moveTemplatePath(
                source.node().getId(),
                source.template(),
                source.templatePath(),
                new TemplatePathRequest(target.parentPath()));
        renameCopiedOrMovedPathIfNeeded(source, target);
    }

    private byte[] initialWriteContent(CloudCorePath path, Set<? extends OpenOption> options) throws IOException {
        ResolvedPath resolved = resolve(path);
        boolean create = options.contains(StandardOpenOption.CREATE);
        boolean createNew = options.contains(StandardOpenOption.CREATE_NEW);

        if (createNew && resolved.kind() != PathKind.MISSING) {
            throw new FileAlreadyExistsException(path.toString());
        }
        if (resolved.kind() == PathKind.MISSING) {
            if (!create && !createNew) {
                throw new NoSuchFileException(path.toString());
            }
            resolveTemplateTarget(path);
            return new byte[0];
        }
        if (resolved.kind() != PathKind.TEMPLATE_FILE) {
            throw new FileSystemException(path.toString(), null, "Path is not a regular file");
        }
        if (options.contains(StandardOpenOption.TRUNCATE_EXISTING) && !options.contains(StandardOpenOption.APPEND)) {
            return new byte[0];
        }
        return read(path);
    }

    private void writeFile(CloudCorePath path, byte[] content) throws IOException {
        ResolvedPath resolved = resolve(path);
        String encoded = Base64.getEncoder().encodeToString(content);
        if (resolved.kind() == PathKind.TEMPLATE_FILE) {
            handshakeClient.saveTemplateFile(
                    resolved.node().getId(),
                    resolved.template(),
                    resolved.templatePath(),
                    new SaveTemplateFileRequest(true, encoded));
            return;
        }

        TemplateTarget target = resolveTemplateTarget(path);
        handshakeClient.uploadTemplateFile(
                target.node().getId(),
                target.template(),
                target.parentPath(),
                new UploadTemplateFileRequest(target.fileName(), encoded));
    }

    private ResolvedPath requireExistingTemplatePath(CloudCorePath path) throws IOException {
        ResolvedPath resolved = resolve(path);
        if (resolved.kind() != PathKind.TEMPLATE_PATH && resolved.kind() != PathKind.TEMPLATE_FILE) {
            throw new NoSuchFileException(path.toString());
        }
        return resolved;
    }

    private TemplateTarget resolveTemplateTarget(CloudCorePath path) throws IOException {
        List<String> parts = path.parts();
        if (parts.size() < 4) {
            throw new AccessDeniedException(path.toString());
        }

        CloudCoreNode node = node(parts.get(0))
                .orElseThrow(() -> new NoSuchFileException(path.toString()));
        String template = parts.get(2);
        boolean templateExists = handshakeClient.getTemplates(node.getId()).stream()
                .anyMatch(candidate -> candidate.name().equals(template));
        if (!templateExists) {
            throw new NoSuchFileException(path.toString());
        }

        String templatePath = String.join("/", parts.subList(3, parts.size()));
        String parentPath = parts.size() == 4 ? "" : String.join("/", parts.subList(3, parts.size() - 1));
        String fileName = parts.get(parts.size() - 1);
        ResolvedPath parent = parts.size() == 4
                ? ResolvedPath.template(node, template)
                : resolve(new CloudCorePath(this, parts.subList(0, parts.size() - 1), true));
        if (parent.kind() != PathKind.TEMPLATE && parent.kind() != PathKind.TEMPLATE_PATH) {
            throw new NoSuchFileException(path.getParent() == null ? path.toString() : path.getParent().toString());
        }
        return new TemplateTarget(node, template, templatePath, parentPath, fileName);
    }

    private void renameCopiedOrMovedPathIfNeeded(ResolvedPath source, TemplateTarget target) {
        if (source.fileName().equals(target.fileName())) {
            return;
        }
        String copiedPath = target.parentPath().isBlank()
                ? source.fileName()
                : target.parentPath() + "/" + source.fileName();
        handshakeClient.renameTemplatePath(
                target.node().getId(),
                target.template(),
                copiedPath,
                new TemplatePathRequest(target.fileName()));
    }

    private void requireSameTemplate(
            ResolvedPath source,
            TemplateTarget target,
            CloudCorePath sourcePath,
            CloudCorePath targetPath
    ) throws IOException {
        if (source.node().getId() != target.node().getId() || !source.template().equals(target.template())) {
            throw new FileSystemException(
                    sourcePath.toString(),
                    targetPath.toString(),
                    "Copying or moving between nodes/templates is not supported");
        }
    }

    private enum PathKind {
        ROOT,
        NODE,
        TEMPLATES,
        TEMPLATE,
        TEMPLATE_PATH,
        TEMPLATE_FILE,
        MISSING
    }

    private record ResolvedPath(
            PathKind kind,
            CloudCoreNode node,
            String template,
            String templatePath
    ) {
        static ResolvedPath root() {
            return new ResolvedPath(PathKind.ROOT, null, null, null);
        }

        static ResolvedPath node(CloudCoreNode node) {
            return new ResolvedPath(PathKind.NODE, node, null, null);
        }

        static ResolvedPath templates(CloudCoreNode node) {
            return new ResolvedPath(PathKind.TEMPLATES, node, null, null);
        }

        static ResolvedPath template(CloudCoreNode node, String template) {
            return new ResolvedPath(PathKind.TEMPLATE, node, template, "");
        }

        static ResolvedPath templatePath(CloudCoreNode node, String template, String templatePath) {
            return new ResolvedPath(PathKind.TEMPLATE_PATH, node, template, templatePath);
        }

        static ResolvedPath templateFile(CloudCoreNode node, String template, String templatePath) {
            return new ResolvedPath(PathKind.TEMPLATE_FILE, node, template, templatePath);
        }

        static ResolvedPath missing() {
            return new ResolvedPath(PathKind.MISSING, null, null, null);
        }

        String parentPath() {
            if (templatePath == null || templatePath.isBlank() || !templatePath.contains("/")) {
                return "";
            }
            return templatePath.substring(0, templatePath.lastIndexOf('/'));
        }

        String fileName() {
            if (templatePath == null || templatePath.isBlank()) {
                return "";
            }
            return templatePath.contains("/")
                    ? templatePath.substring(templatePath.lastIndexOf('/') + 1)
                    : templatePath;
        }
    }

    private record TemplateTarget(
            CloudCoreNode node,
            String template,
            String templatePath,
            String parentPath,
            String fileName
    ) {
    }

    private static class CloudCoreFileSystemProvider extends FileSystemProvider {
        private final CloudCoreFileSystem fileSystem;

        private CloudCoreFileSystemProvider(CloudCoreFileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        @Override
        public String getScheme() {
            return "cloudcore";
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
            throw new FileSystemAlreadyExistsException();
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            if (!fileSystem.isOpen()) {
                throw new FileSystemNotFoundException();
            }
            return fileSystem;
        }

        @Override
        public Path getPath(URI uri) {
            return fileSystem.getPath(uri.getPath());
        }

        private static boolean isWriteOption(Set<? extends OpenOption> options) {
            return options.contains(StandardOpenOption.WRITE)
                    || options.contains(StandardOpenOption.APPEND)
                    || options.contains(StandardOpenOption.CREATE)
                    || options.contains(StandardOpenOption.CREATE_NEW)
                    || options.contains(StandardOpenOption.DELETE_ON_CLOSE)
                    || options.contains(StandardOpenOption.TRUNCATE_EXISTING);
        }

        @Override
        public FileChannel newFileChannel(
                Path path,
                Set<? extends OpenOption> options,
                FileAttribute<?>... attrs
        ) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(path);

                if (isWriteOption(options)) {
                    Path tempFile = Files.createTempFile("cloudcore-sftp-write-", ".tmp");
                    Files.write(tempFile, fileSystem.initialWriteContent(cloudPath, options));
                    FileChannel delegate = FileChannel.open(
                            tempFile,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    if (options.contains(StandardOpenOption.APPEND)) {
                        delegate.position(delegate.size());
                    }
                    tempFile.toFile().deleteOnExit();
                    return new CommittingFileChannel(delegate, tempFile, bytes -> fileSystem.writeFile(cloudPath, bytes));
                }

                Path tempFile = Files.createTempFile("cloudcore-sftp-", ".tmp");
                Files.write(tempFile, fileSystem.read(cloudPath));
                tempFile.toFile().deleteOnExit();

                return FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to open file channel for '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public SeekableByteChannel newByteChannel(
                Path path,
                Set<? extends OpenOption> options,
                FileAttribute<?>... attrs
        ) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(path);

                if (isWriteOption(options)) {
                    byte[] initialContent = fileSystem.initialWriteContent(cloudPath, options);
                    WritableByteArrayChannel channel = new WritableByteArrayChannel(
                            initialContent,
                            bytes -> fileSystem.writeFile(cloudPath, bytes));
                    if (options.contains(StandardOpenOption.APPEND)) {
                        channel.position(initialContent.length);
                    }
                    return channel;
                }

                return new ByteArrayChannel(fileSystem.read(cloudPath));
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to open file '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(
                Path dir,
                DirectoryStream.Filter<? super Path> filter
        ) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(dir);
                List<Path> entries = new ArrayList<>();
                for (Path entry : fileSystem.list(cloudPath)) {
                    if (filter == null || filter.accept(entry)) {
                        entries.add(entry);
                    }
                }
                return new DirectoryStream<>() {
                    @Override
                    public Iterator<Path> iterator() {
                        return entries.iterator();
                    }

                    @Override
                    public void close() {
                    }
                };
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to list directory '{}'", dir, exception);
                throw exception;
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(dir);
                fileSystem.createDirectory(cloudPath);
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to create directory '{}'", dir, exception);
                throw exception;
            }
        }

        @Override
        public void delete(Path path) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(path);
                fileSystem.delete(cloudPath);
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to delete '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            try {
                CloudCorePath sourcePath = checkPath(source);
                CloudCorePath targetPath = checkPath(target);

                fileSystem.copy(sourcePath, targetPath, hasReplaceExisting(options));
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to copy '{}' to '{}'", source, target, exception);
                throw exception;
            }
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            try {
                CloudCorePath sourcePath = checkPath(source);
                CloudCorePath targetPath = checkPath(target);

                fileSystem.move(sourcePath, targetPath, hasReplaceExisting(options));
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to move '{}' to '{}'", source, target, exception);
                throw exception;
            }
        }

        @Override
        public boolean isSameFile(Path path, Path path2) throws IOException {
            try {
                return checkPath(path).toAbsolutePath().equals(checkPath(path2).toAbsolutePath());
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to compare '{}' and '{}'", path, path2, exception);
                throw exception;
            }
        }

        @Override
        public boolean isHidden(Path path) {
            return false;
        }

        @Override
        public FileStore getFileStore(Path path) {
            throw new UnsupportedOperationException("File stores are not supported");
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(path);

                fileSystem.attributes(cloudPath);

                for (AccessMode mode : modes) {
                    if (mode == AccessMode.WRITE) {
                        cloudPath.requireWritable();
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed access check for '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(
                Path path,
                Class<V> type,
                LinkOption... options
        ) {
            try {
                if (type == BasicFileAttributeView.class) {
                    return type.cast(new BasicFileAttributeView() {
                        @Override
                        public String name() {
                            return "basic";
                        }

                        @Override
                        public BasicFileAttributes readAttributes() throws IOException {
                            try {
                                return fileSystem.attributes(checkPath(path));
                            } catch (IOException | RuntimeException exception) {
                                LOGGER.error("Failed to read attribute view for '{}'", path, exception);
                                throw exception;
                            }
                        }

                        @Override
                        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
                            throw new ReadOnlyFileSystemException();
                        }
                    });
                }
                return null;
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to get attribute view for '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(
                Path path,
                Class<A> type,
                LinkOption... options
        ) throws IOException {
            try {
                if (!type.isAssignableFrom(CloudCoreAttributes.class)
                        && !type.isAssignableFrom(BasicFileAttributes.class)) {
                    throw new UnsupportedOperationException("Only basic attributes are supported");
                }
                return type.cast(fileSystem.attributes(checkPath(path)));
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to read attributes for '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public Map<String, Object> readAttributes(
                Path path,
                String attributes,
                LinkOption... options
        ) throws IOException {
            try {
                CloudCoreAttributes basic = fileSystem.attributes(checkPath(path));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("isRegularFile", basic.isRegularFile());
                result.put("isDirectory", basic.isDirectory());
                result.put("isSymbolicLink", basic.isSymbolicLink());
                result.put("isOther", basic.isOther());
                result.put("size", basic.size());
                result.put("creationTime", basic.creationTime());
                result.put("lastAccessTime", basic.lastAccessTime());
                result.put("lastModifiedTime", basic.lastModifiedTime());
                result.put("fileKey", basic.fileKey());
                result.put("permissions", basic.permissions());
                result.put("owner", basic.owner());
                result.put("group", basic.group());
                return result;
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to read attribute map for '{}'", path, exception);
                throw exception;
            }
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            try {
                CloudCorePath cloudPath = checkPath(path);
                cloudPath.requireWritable();

                throw new UnsupportedOperationException("Setting attributes is not implemented yet");
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to set attribute '{}' for '{}'", attribute, path, exception);
                throw exception;
            }
        }

        private CloudCorePath checkPath(Path path) {
            if (!(path instanceof CloudCorePath cloudPath) || cloudPath.getFileSystem() != fileSystem) {
                throw new ProviderMismatchException();
            }
            fileSystem.ensureOpen();
            return cloudPath;
        }

        private boolean hasReplaceExisting(CopyOption... options) {
            for (CopyOption option : options) {
                if (option == StandardCopyOption.REPLACE_EXISTING) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class CloudCorePath implements Path {
        private final CloudCoreFileSystem fileSystem;
        private final List<String> parts;
        private final boolean absolute;

        private CloudCorePath(CloudCoreFileSystem fileSystem, List<String> parts, boolean absolute) {
            this.fileSystem = fileSystem;
            this.parts = List.copyOf(parts);
            this.absolute = absolute;
        }

        private static CloudCorePath parse(CloudCoreFileSystem fileSystem, String raw) {
            if (raw == null) {
                throw new InvalidPathException("", "Path must not be null");
            }
            boolean absolute = raw.startsWith("/");
            List<String> parts = new ArrayList<>();
            for (String part : raw.split("/+")) {
                if (part.isBlank() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    if (!parts.isEmpty()) {
                        parts.remove(parts.size() - 1);
                    }
                    continue;
                }
                parts.add(part);
            }
            return new CloudCorePath(fileSystem, parts, absolute);
        }

        public boolean isWritable() {
            return switch (fileSystem.resolve(this).kind()) {
                case TEMPLATE,
                     TEMPLATE_PATH,
                     TEMPLATE_FILE -> true;

                case ROOT,
                     NODE,
                     TEMPLATES,
                     MISSING -> false;
            };
        }
        public void requireWritable() throws AccessDeniedException {
            if (!isWritable()) {
                throw new AccessDeniedException(this.toString());
            }
        }

        private List<String> parts() {
            return parts;
        }

        @Override
        public CloudCoreFileSystem getFileSystem() {
            return fileSystem;
        }

        @Override
        public boolean isAbsolute() {
            return absolute;
        }

        @Override
        public Path getRoot() {
            return absolute ? new CloudCorePath(fileSystem, List.of(), true) : null;
        }

        @Override
        public Path getFileName() {
            if (parts.isEmpty()) {
                return null;
            }
            return new CloudCorePath(fileSystem, List.of(parts.get(parts.size() - 1)), false);
        }

        @Override
        public CloudCorePath getParent() {
            if (parts.isEmpty()) {
                return null;
            }
            return new CloudCorePath(fileSystem, parts.subList(0, parts.size() - 1), absolute);
        }

        @Override
        public int getNameCount() {
            return parts.size();
        }

        @Override
        public CloudCorePath getName(int index) {
            return new CloudCorePath(fileSystem, List.of(parts.get(index)), false);
        }

        @Override
        public CloudCorePath subpath(int beginIndex, int endIndex) {
            return new CloudCorePath(fileSystem, parts.subList(beginIndex, endIndex), false);
        }

        @Override
        public boolean startsWith(Path other) {
            CloudCorePath otherPath = asCloudPath(other);
            return startsWith(otherPath.parts);
        }

        @Override
        public boolean startsWith(String other) {
            return startsWith(parse(fileSystem, other).parts);
        }

        private boolean startsWith(List<String> otherParts) {
            return parts.size() >= otherParts.size()
                    && parts.subList(0, otherParts.size()).equals(otherParts);
        }

        @Override
        public boolean endsWith(Path other) {
            CloudCorePath otherPath = asCloudPath(other);
            return endsWith(otherPath.parts);
        }

        @Override
        public boolean endsWith(String other) {
            return endsWith(parse(fileSystem, other).parts);
        }

        private boolean endsWith(List<String> otherParts) {
            return parts.size() >= otherParts.size()
                    && parts.subList(parts.size() - otherParts.size(), parts.size()).equals(otherParts);
        }

        @Override
        public Path normalize() {
            return this;
        }

        @Override
        public CloudCorePath resolve(Path other) {
            CloudCorePath otherPath = asCloudPath(other);
            if (otherPath.isAbsolute()) {
                return otherPath;
            }
            List<String> next = new ArrayList<>(parts);
            next.addAll(otherPath.parts);
            return new CloudCorePath(fileSystem, next, absolute);
        }

        @Override
        public CloudCorePath resolve(String other) {
            return resolve(parse(fileSystem, other));
        }

        @Override
        public CloudCorePath resolveSibling(Path other) {
            CloudCorePath parent = getParent();
            CloudCorePath o = asCloudPath(other);
            return parent == null ? o : parent.resolve(other);
        }

        @Override
        public CloudCorePath resolveSibling(String other) {
            return resolveSibling(parse(fileSystem, other));
        }

        @Override
        public CloudCorePath relativize(Path other) {
            CloudCorePath otherPath = asCloudPath(other);
            if (absolute != otherPath.absolute) {
                throw new IllegalArgumentException("Cannot relativize absolute and relative paths");
            }
            int common = 0;
            while (common < parts.size()
                    && common < otherPath.parts.size()
                    && parts.get(common).equals(otherPath.parts.get(common))) {
                common++;
            }
            List<String> next = new ArrayList<>();
            for (int index = common; index < parts.size(); index++) {
                next.add("..");
            }
            next.addAll(otherPath.parts.subList(common, otherPath.parts.size()));
            return new CloudCorePath(fileSystem, next, false);
        }

        @Override
        public URI toUri() {
            return URI.create("cloudcore:" + toAbsolutePath());
        }

        @Override
        public CloudCorePath toAbsolutePath() {
            return absolute ? this : new CloudCorePath(fileSystem, parts, true);
        }

        @Override
        public CloudCorePath toRealPath(LinkOption... options) {
            return toAbsolutePath();
        }

        @Override
        public WatchKey register(
                WatchService watcher,
                WatchEvent.Kind<?>[] events,
                WatchEvent.Modifier... modifiers
        ) {
            throw new UnsupportedOperationException("Watch service is not supported");
        }

        @Override
        public int compareTo(Path other) {
            return toString().compareTo(other.toString());
        }

        @Override
        public Iterator<Path> iterator() {
            return parts.stream()
                    .map(part -> (Path) new CloudCorePath(fileSystem, List.of(part), false))
                    .iterator();
        }

        @Override
        public String toString() {
            if (parts.isEmpty()) {
                return absolute ? "/" : "";
            }
            return (absolute ? "/" : "") + String.join("/", parts);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof CloudCorePath other
                    && fileSystem == other.fileSystem
                    && absolute == other.absolute
                    && parts.equals(other.parts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(fileSystem), absolute, parts);
        }

        private CloudCorePath asCloudPath(Path path) {
            if (!(path instanceof CloudCorePath cloudPath) || cloudPath.fileSystem != fileSystem) {
                throw new ProviderMismatchException();
            }
            return cloudPath;
        }
    }

    private record CloudCoreAttributes(boolean directoryFlag, long size) implements PosixFileAttributes {
        private static final FileTime EPOCH = FileTime.fromMillis(0);

        static CloudCoreAttributes directory() {
            return new CloudCoreAttributes(true, 0);
        }

        static CloudCoreAttributes file(long size) {
            return new CloudCoreAttributes(false, size);
        }

        @Override
        public FileTime lastModifiedTime() {
            return EPOCH;
        }

        @Override
        public FileTime lastAccessTime() {
            return EPOCH;
        }

        @Override
        public FileTime creationTime() {
            return EPOCH;
        }

        @Override
        public boolean isRegularFile() {
            return !directoryFlag;
        }

        @Override
        public boolean isDirectory() {
            return directoryFlag;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public Object fileKey() {
            return null;
        }

        @Override
        public UserPrincipal owner() {
            return ()->"cloudcore";
        }

        @Override
        public GroupPrincipal group() {
            return ()->"cloudcore";
        }

        @Override
        public Set<PosixFilePermission> permissions() {
            if (directoryFlag) {
                return Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                );
            }

            return Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ
            );
        }
    }

    @FunctionalInterface
    private interface ByteCommitter {
        void commit(byte[] bytes) throws IOException;
    }

    private static class CommittingFileChannel extends FileChannel {
        private final FileChannel delegate;
        private final Path tempFile;
        private final ByteCommitter committer;

        private CommittingFileChannel(FileChannel delegate, Path tempFile, ByteCommitter committer) {
            this.delegate = delegate;
            this.tempFile = tempFile;
            this.committer = committer;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return delegate.read(dsts, offset, length);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return delegate.write(src);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public void force(boolean metaData) throws IOException {
            delegate.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, java.nio.channels.WritableByteChannel target) throws IOException {
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(java.nio.channels.ReadableByteChannel src, long position, long count) throws IOException {
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return delegate.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            return delegate.write(src, position);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            IOException failure = null;
            try {
                delegate.close();
                committer.commit(Files.readAllBytes(tempFile));
            } catch (IOException exception) {
                failure = exception;
                throw exception;
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException exception) {
                    if (failure != null) {
                        failure.addSuppressed(exception);
                    } else {
                        throw exception;
                    }
                }
            }
        }
    }

    private static class ByteArrayChannel implements SeekableByteChannel {
        private final byte[] bytes;
        private int position;
        private boolean open = true;

        private ByteArrayChannel(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read(ByteBuffer dst) {
            ensureOpen();
            if (position >= bytes.length) {
                return -1;
            }
            int length = Math.min(dst.remaining(), bytes.length - position);
            dst.put(bytes, position, length);
            position += length;
            return length;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public long position() {
            ensureOpen();
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            ensureOpen();
            if (newPosition < 0 || newPosition > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid position");
            }
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() {
            ensureOpen();
            return bytes.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        private void ensureOpen() {
            if (!open) {
                throw new ClosedFileSystemException();
            }
        }
    }

    private static class WritableByteArrayChannel implements SeekableByteChannel {
        private byte[] bytes;
        private int position;
        private int size;
        private boolean open = true;
        private final ByteCommitter committer;

        private WritableByteArrayChannel(byte[] initialContent, ByteCommitter committer) {
            this.bytes = Arrays.copyOf(initialContent, Math.max(initialContent.length, 32));
            this.size = initialContent.length;
            this.committer = committer;
        }

        @Override
        public int read(ByteBuffer dst) {
            ensureOpen();
            if (position >= size) {
                return -1;
            }
            int length = Math.min(dst.remaining(), size - position);
            dst.put(bytes, position, length);
            position += length;
            return length;
        }

        @Override
        public int write(ByteBuffer src) {
            ensureOpen();
            int length = src.remaining();
            ensureCapacity(position + length);
            src.get(bytes, position, length);
            position += length;
            size = Math.max(size, position);
            return length;
        }

        @Override
        public long position() {
            ensureOpen();
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            ensureOpen();
            if (newPosition < 0 || newPosition > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid position");
            }
            position = (int) newPosition;
            ensureCapacity(position);
            return this;
        }

        @Override
        public long size() {
            ensureOpen();
            return size;
        }

        @Override
        public SeekableByteChannel truncate(long newSize) {
            ensureOpen();
            if (newSize < 0 || newSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid size");
            }
            size = (int) Math.min(size, newSize);
            if (position > size) {
                position = size;
            }
            return this;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            if (!open) {
                return;
            }
            open = false;
            committer.commit(Arrays.copyOf(bytes, size));
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= bytes.length) {
                return;
            }
            int nextCapacity = bytes.length;
            while (nextCapacity < capacity) {
                nextCapacity *= 2;
            }
            bytes = Arrays.copyOf(bytes, nextCapacity);
        }

        private void ensureOpen() {
            if (!open) {
                throw new ClosedFileSystemException();
            }
        }
    }

}
