import {createContext, useContext, useMemo, useRef, useState} from "react";
import type {ReactNode} from "react";
import styles from "./Toast.module.css";

type ToastType = "success" | "error";

type ToastContextValue = {
    showToast: (message: string) => void;
    showError: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

type ToastProviderProps = {
    children: ReactNode;
}

function ToastProvider({children}: ToastProviderProps) {
    const [toast, setToast] = useState<{message: string; type: ToastType} | null>(null);
    const timeoutRef = useRef<number | null>(null);

    const value = useMemo<ToastContextValue>(() => ({
        showToast(nextMessage: string) {
            show(nextMessage, "success");
        },
        showError(nextMessage: string) {
            show(nextMessage, "error");
        }
    }), []);

    function show(nextMessage: string, type: ToastType) {
            if (timeoutRef.current !== null) {
                window.clearTimeout(timeoutRef.current);
            }
            setToast({message: nextMessage, type});
            timeoutRef.current = window.setTimeout(() => {
                setToast(null);
                timeoutRef.current = null;
            }, type === "error" ? 5200 : 2600);
    }

    return (
        <ToastContext.Provider value={value}>
            {children}
            {toast && (
                <div
                    className={styles.toastHost}
                    role={toast.type === "error" ? "alert" : "status"}
                    aria-live={toast.type === "error" ? "assertive" : "polite"}
                >
                    <div className={`${styles.toast} ${styles[toast.type]}`}>{toast.message}</div>
                </div>
            )}
        </ToastContext.Provider>
    );
}

export function useToast(): ToastContextValue {
    const context = useContext(ToastContext);
    if (context === null) {
        throw new Error("useToast must be used inside ToastProvider");
    }
    return context;
}

export default ToastProvider;
