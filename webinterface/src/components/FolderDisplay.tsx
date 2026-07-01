import type {FileInFolder, FolderResponse} from "../lib/api.ts";
import styles from "./FolderDisplay.module.css";
import {getFileIcon, getFolderIcon} from "../lib/FileIcons.tsx";
import {useEffect, useRef, useState} from "react";
import type {DragEvent} from "react";
import {FaCopy, FaDownload, FaEdit, FaEllipsisV, FaFolderPlus, FaFileExport, FaTrash} from "react-icons/fa";
import {useI18n} from "../lib/i18n.ts";

type FolderDisplayProps = {
    folder: FolderResponse;
    onOpen: (name: string) => void;
    onDownload: (name: string, isFile: boolean) => Promise<void>;
    onDelete: (name: string) => Promise<void>;
    onRename: (name: string, nextName: string) => Promise<void>;
    onCopy: (name: string, destinationFolderPath: string) => Promise<void>;
    onMove: (name: string, destinationFolderPath: string) => Promise<void>;
    onCreateFolder: (name: string) => Promise<void>;
    onUpload: (uploads: Array<{file: File; relativePath: string}>) => Promise<void>;
    isRoot: boolean;
    canOpen: boolean;
    canDownload: boolean;
    canWrite: boolean;
    canCreate: boolean;
}

type FileSystemEntryLike = {
    name: string;
    isFile: boolean;
    isDirectory: boolean;
}

type FileSystemFileEntryLike = FileSystemEntryLike & {
    file: (success: (file: File) => void, error?: (reason: DOMException) => void) => void;
}

type FileSystemDirectoryEntryLike = FileSystemEntryLike & {
    createReader: () => {
        readEntries: (
            success: (entries: FileSystemEntryLike[]) => void,
            error?: (reason: DOMException) => void
        ) => void;
    };
}

type DataTransferItemWithEntry = DataTransferItem & {
    webkitGetAsEntry?: () => FileSystemEntryLike | null;
}

function FolderDisplay({
    folder,
    onOpen,
    onDownload,
    onDelete,
    onRename,
    onCopy,
    onMove,
    onCreateFolder,
    onUpload,
    isRoot,
    canOpen,
    canDownload,
    canWrite,
    canCreate
}: FolderDisplayProps) {
    const {t} = useI18n();
    const rootRef = useRef<HTMLDivElement | null>(null);
    const fileInput = useRef<HTMLInputElement | null>(null);
    const folderInput = useRef<HTMLInputElement | null>(null);
    const [uploadOpen, setUploadOpen] = useState(false);
    const [actionOpen, setActionOpen] = useState<string | null>(null);
    const [dragging, setDragging] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const files : FileInFolder[] = (isRoot ? [...folder.files] : [{isFile: false, name: ".."},...folder.files]).sort((left, right) => {
        if (left.name === "..") {
            return -1;
        }
        if (left.isFile !== right.isFile) {
            return left.isFile ? 1 : -1;
        }
        return left.name.localeCompare(right.name);
    });



    useEffect(() => {
        function closeMenus(event: MouseEvent) {
            const target = event.target as Node;
            const targetElement = target instanceof Element ? target : null;

            if (rootRef.current && !rootRef.current.contains(target)) {
                setUploadOpen(false);
                setActionOpen(null);
                return;
            }

            if (!targetElement?.closest("[data-folder-action-menu]")) {
                setActionOpen(null);
            }
        }

        document.addEventListener("mousedown", closeMenus);
        return () => document.removeEventListener("mousedown", closeMenus);
    }, []);

    async function upload(files: FileList | File[]) {
        await uploadEntries(Array.from(files)
            .filter(file => file.size >= 0)
            .map(file => {
                const path = (file as File & {webkitRelativePath?: string}).webkitRelativePath;
                return {
                    file,
                    relativePath: path && path.length > 0 ? path : file.name
                };
            }));
    }

    async function uploadEntries(uploads: Array<{file: File; relativePath: string}>) {
        if (!canCreate) return;
        if (uploads.length === 0) return;

        try {
            setUploading(true);
            setError(null);
            await onUpload(uploads);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.upload_failed"));
        } finally {
            setUploading(false);
            setUploadOpen(false);
        }
    }

    async function uploadsFromDrop(event: DragEvent<HTMLDivElement>): Promise<Array<{file: File; relativePath: string}>> {
        const entries = Array.from(event.dataTransfer.items)
            .map(item => ((item as DataTransferItemWithEntry).webkitGetAsEntry?.() ?? null) as FileSystemEntryLike | null)
            .filter((entry): entry is FileSystemEntryLike => entry !== null);

        if (entries.length === 0) {
            return Array.from(event.dataTransfer.files).map(file => ({
                file,
                relativePath: file.name
            }));
        }

        const nested = await Promise.all(entries.map(entry => readEntrySafe(entry, "")));
        return nested.flat();
    }

    async function readEntrySafe(entry: FileSystemEntryLike, parentPath: string): Promise<Array<{file: File; relativePath: string}>> {
        try {
            return await readEntry(entry, parentPath);
        } catch {
            return [];
        }
    }

    async function readEntry(entry: FileSystemEntryLike, parentPath: string): Promise<Array<{file: File; relativePath: string}>> {
        const relativePath = [parentPath, entry.name].filter(Boolean).join("/");

        if (entry.isFile) {
            const file = await readFileEntry(entry as FileSystemFileEntryLike);
            return [{file, relativePath}];
        }

        if (entry.isDirectory) {
            const children = await readDirectoryEntries(entry as FileSystemDirectoryEntryLike);
            const nested = await Promise.all(children.map(child => readEntrySafe(child, relativePath)));
            return nested.flat();
        }

        return [];
    }

    function readFileEntry(entry: FileSystemFileEntryLike): Promise<File> {
        return new Promise((resolve, reject) => {
            entry.file(resolve, reject);
        });
    }

    function readDirectoryEntries(entry: FileSystemDirectoryEntryLike): Promise<FileSystemEntryLike[]> {
        const reader = entry.createReader();
        const entries: FileSystemEntryLike[] = [];

        return new Promise((resolve, reject) => {
            function readBatch() {
                reader.readEntries(batch => {
                    if (batch.length === 0) {
                        resolve(entries);
                        return;
                    }
                    entries.push(...batch);
                    readBatch();
                }, reject);
            }

            readBatch();
        });
    }

    async function download(name: string, isFile: boolean) {
        if (!canDownload) return;
        try {
            setError(null);
            setActionOpen(null);
            await onDownload(name, isFile);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.download_failed"));
        }
    }

    async function deleteEntry(name: string) {
        if (!canWrite) return;
        if (!window.confirm(t("confirm.delete").replace("{name}", name))) {
            return;
        }

        try {
            setError(null);
            setActionOpen(null);
            await onDelete(name);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.delete_failed"));
        }
    }

    async function createFolder() {
        if (!canCreate) return;
        const name = window.prompt(t("template.new_folder_name"));
        if (name === null || name.trim().length === 0) {
            return;
        }

        try {
            setError(null);
            await onCreateFolder(name.trim());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.create_folder_failed"));
        }
    }

    async function renameEntry(name: string) {
        if (!canWrite) return;
        const nextName = window.prompt(t("field.name"), name);
        if (nextName === null || nextName.trim().length === 0 || nextName.trim() === name) {
            return;
        }

        try {
            setError(null);
            setActionOpen(null);
            await onRename(name, nextName.trim());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.rename_failed"));
        }
    }

    async function copyEntry(name: string) {
        if (!canCreate) return;
        const destination = window.prompt(t("template.copy_destination"), "");
        if (destination === null) {
            return;
        }

        try {
            setError(null);
            setActionOpen(null);
            await onCopy(name, destination.trim());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.copy_failed"));
        }
    }

    async function moveEntry(name: string) {
        if (!canWrite) return;
        const destination = window.prompt(t("template.move_destination"), "");
        if (destination === null) {
            return;
        }

        try {
            setError(null);
            setActionOpen(null);
            await onMove(name, destination.trim());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.move_failed"));
        }
    }

    return (
        <div
            ref={rootRef}
            className={`${styles.folder} ${dragging ? styles.dragging : ""}`}
            onDragOver={event => {
                if (!canCreate) return;
                event.preventDefault();
                setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={event => {
                event.preventDefault();
                setDragging(false);
                if (!canCreate) return;
                uploadsFromDrop(event)
                    .then(uploads => {
                        if (uploads.length === 0) {
                            setError(t("error.no_readable_files"));
                            return;
                        }
                        return uploadEntries(uploads);
                    })
                    .catch(reason => setError(reason instanceof Error ? reason.message : t("error.access_denied")));
            }}
        >
            <div className={styles.folderContent}>
                <div className={styles.toolbar}>
                    {canCreate && (
                        <button type="button" className={styles.createFolderButton} onClick={createFolder}>
                            <FaFolderPlus aria-hidden/>
                            {t("action.new_folder")}
                        </button>
                    )}
                    {canCreate && (
                        <div className={styles.uploadWrap}>
                            <button type="button" className={styles.uploadButton} onClick={() => setUploadOpen(current => !current)} disabled={uploading}>
                                {uploading ? t("state.uploading") : t("action.upload")}
                            </button>
                            {uploadOpen && (
                                <div className={styles.uploadMenu}>
                                    <button type="button" onClick={() => fileInput.current?.click()}>{t("template.file")}</button>
                                    <button type="button" onClick={() => folderInput.current?.click()}>{t("template.folder")}</button>
                                </div>
                            )}
                        </div>
                    )}
                    <input
                        ref={fileInput}
                        type="file"
                        hidden
                        multiple
                        onChange={event => {
                            if (event.target.files) {
                                upload(event.target.files);
                                event.target.value = "";
                            }
                        }}
                    />
                    <input
                        ref={element => {
                            folderInput.current = element;
                            element?.setAttribute("webkitdirectory", "");
                            element?.setAttribute("directory", "");
                        }}
                        type="file"
                        hidden
                        multiple
                        onChange={event => {
                            if (event.target.files) {
                                upload(event.target.files);
                                event.target.value = "";
                            }
                        }}
                    />
                </div>
                {error && <p className={styles.error}>{error}</p>}
                {files.length === 0 && <p className={styles.empty}>{t("template.folder_empty")}</p>}
                {files.map(file => {
                    const entryKey = `${file.isFile ? "file" : "folder"}-${file.name}`;
                    const hasActions = file.name !== "..";

                    return (
                        <div key={entryKey} className={styles.entry}>
                            <button
                                className={`${styles.entryMain} ${!canOpen ? styles.entryDisabled : ""}`}
                                type="button"
                                disabled={!canOpen}
                                onClick={() => onOpen(file.name)}
                            >
                                {file.isFile
                                    ? getFileIcon(file.name, {className: styles.icon, "aria-hidden": true})
                                    : getFolderIcon(file.name, {className: styles.icon, "aria-hidden": true})
                                }
                                <span className={styles.name}>{file.name}</span>
                            </button>
                            {hasActions && (
                                <div className={styles.actionWrap} data-folder-action-menu>
                                    <button
                                        className={styles.actionButton}
                                        type="button"
                                        aria-label={t("action.actions_for").replace("{name}", file.name)}
                                        aria-haspopup="menu"
                                        aria-expanded={actionOpen === entryKey}
                                        onClick={() => setActionOpen(current => current === entryKey ? null : entryKey)}
                                    >
                                        <FaEllipsisV aria-hidden/>
                                    </button>
                                    {actionOpen === entryKey && (
                                        <div className={styles.actionMenu} role="menu">
                                            {canDownload && (
                                                <button type="button" role="menuitem" onClick={() => download(file.name, file.isFile)}>
                                                    <FaDownload aria-hidden/>
                                                    {t("action.download")}
                                                </button>
                                            )}
                                            {canWrite && (
                                                <button type="button" role="menuitem" onClick={() => renameEntry(file.name)}>
                                                    <FaEdit aria-hidden/>
                                                    {t("action.rename")}
                                                </button>
                                            )}
                                            {canCreate && (
                                                <button type="button" role="menuitem" onClick={() => copyEntry(file.name)}>
                                                    <FaCopy aria-hidden/>
                                                    {t("action.copy_to")}
                                                </button>
                                            )}
                                            {canWrite && (
                                                <button type="button" role="menuitem" onClick={() => moveEntry(file.name)}>
                                                    <FaFileExport aria-hidden/>
                                                    {t("action.move_to")}
                                                </button>
                                            )}
                                            {canWrite && (
                                                <button type="button" role="menuitem" className={styles.dangerAction} onClick={() => deleteEntry(file.name)}>
                                                    <FaTrash aria-hidden/>
                                                    {t("action.delete")}
                                                </button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export default FolderDisplay;
