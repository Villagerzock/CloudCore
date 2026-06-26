package net.villagerzock.backend.sftp;

import net.villagerzock.backend.dto.FileSystemDelegatorResponse;
import net.villagerzock.backend.dto.FolderResponse;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.entity.CloudCoreNode;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.service.NodeHandshakeClient;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
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
        return true;
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
        ResolvedPath resolved = resolve(path);
        return switch (resolved.kind()) {
            case ROOT -> accessibleNodes().stream()
                    .map(node -> path.resolve(node.getName()))
                    .toList();
            case NODE -> List.of(path.resolve("templates"));
            case TEMPLATES -> handshakeClient.getTemplates(resolved.node().getId()).stream()
                    .map(ServerTemplateDto::name)
                    .map(path::resolve)
                    .toList();
            case TEMPLATE, TEMPLATE_PATH -> listTemplatePath(path, resolved);
            case TEMPLATE_FILE -> throw new NotDirectoryException(path.toString());
            case MISSING -> throw new NoSuchFileException(path.toString());
        };
    }

    private List<Path> listTemplatePath(CloudCorePath path, ResolvedPath resolved) throws IOException {
        FileSystemDelegatorResponse response = templateResponse(resolved);
        if (response.isFile()) {
            throw new NotDirectoryException(path.toString());
        }
        List<FolderResponse.FileInFolder> files = response.files() == null ? List.of() : response.files();
        return files.stream()
                .map(FolderResponse.FileInFolder::name)
                .map(path::resolve)
                .toList();
    }

    private byte[] read(CloudCorePath path) throws IOException {
        ResolvedPath resolved = resolve(path);
        if (resolved.kind() != PathKind.TEMPLATE_FILE) {
            throw new NoSuchFileException(path.toString());
        }
        ResponseEntity<byte[]> response = handshakeClient.getTemplateFileContent(
                resolved.node().getId(),
                resolved.template(),
                resolved.templatePath());
        return response.getBody() == null ? new byte[0] : response.getBody();
    }

    private CloudCoreAttributes attributes(CloudCorePath path) throws IOException {
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
            return ResolvedPath.missing();
        }
    }

    private FileSystemDelegatorResponse templateResponse(ResolvedPath resolved) {
        return handshakeClient.getTemplateFileSystemPath(
                resolved.node().getId(),
                resolved.template(),
                resolved.templatePath() == null ? "" : resolved.templatePath());
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

        @Override
        public SeekableByteChannel newByteChannel(
                Path path,
                Set<? extends OpenOption> options,
                FileAttribute<?>... attrs
        ) throws IOException {
            CloudCorePath cloudPath = checkPath(path);
            if (options.stream().anyMatch(option -> option != StandardOpenOption.READ)) {
                throw new ReadOnlyFileSystemException();
            }
            return new ByteArrayChannel(fileSystem.read(cloudPath));
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(
                Path dir,
                DirectoryStream.Filter<? super Path> filter
        ) throws IOException {
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
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public void delete(Path path) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            throw new ReadOnlyFileSystemException();
        }

        @Override
        public boolean isSameFile(Path path, Path path2) {
            return checkPath(path).toAbsolutePath().equals(checkPath(path2).toAbsolutePath());
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
            for (AccessMode mode : modes) {
                if (mode != AccessMode.READ) {
                    throw new ReadOnlyFileSystemException();
                }
            }
            fileSystem.attributes(checkPath(path));
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(
                Path path,
                Class<V> type,
                LinkOption... options
        ) {
            if (type == BasicFileAttributeView.class) {
                return type.cast(new BasicFileAttributeView() {
                    @Override
                    public String name() {
                        return "basic";
                    }

                    @Override
                    public BasicFileAttributes readAttributes() throws IOException {
                        return fileSystem.attributes(checkPath(path));
                    }

                    @Override
                    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
                        throw new ReadOnlyFileSystemException();
                    }
                });
            }
            return null;
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(
                Path path,
                Class<A> type,
                LinkOption... options
        ) throws IOException {
            if (!type.isAssignableFrom(CloudCoreAttributes.class)
                    && !type.isAssignableFrom(BasicFileAttributes.class)) {
                throw new UnsupportedOperationException("Only basic attributes are supported");
            }
            return type.cast(fileSystem.attributes(checkPath(path)));
        }

        @Override
        public Map<String, Object> readAttributes(
                Path path,
                String attributes,
                LinkOption... options
        ) throws IOException {
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
            return result;
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
            throw new ReadOnlyFileSystemException();
        }

        private CloudCorePath checkPath(Path path) {
            if (!(path instanceof CloudCorePath cloudPath) || cloudPath.getFileSystem() != fileSystem) {
                throw new ProviderMismatchException();
            }
            fileSystem.ensureOpen();
            return cloudPath;
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
        public Path getParent() {
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
        public Path getName(int index) {
            return new CloudCorePath(fileSystem, List.of(parts.get(index)), false);
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
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
        public Path resolve(Path other) {
            CloudCorePath otherPath = asCloudPath(other);
            if (otherPath.isAbsolute()) {
                return otherPath;
            }
            List<String> next = new ArrayList<>(parts);
            next.addAll(otherPath.parts);
            return new CloudCorePath(fileSystem, next, absolute);
        }

        @Override
        public Path resolve(String other) {
            return resolve(parse(fileSystem, other));
        }

        @Override
        public Path resolveSibling(Path other) {
            Path parent = getParent();
            return parent == null ? other : parent.resolve(other);
        }

        @Override
        public Path resolveSibling(String other) {
            return resolveSibling(parse(fileSystem, other));
        }

        @Override
        public Path relativize(Path other) {
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
        public Path toAbsolutePath() {
            return absolute ? this : new CloudCorePath(fileSystem, parts, true);
        }

        @Override
        public Path toRealPath(LinkOption... options) {
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

    private record CloudCoreAttributes(boolean directoryFlag, long size) implements BasicFileAttributes {
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

    private static class NotDirectoryException extends IOException {
        private NotDirectoryException(String path) {
            super(path + " is not a directory");
        }
    }
}
