import {createContext, useContext, useMemo, useRef, useState} from "react";
import type {ReactNode} from "react";
import styles from "./Toast.module.css";

type ToastContextValue = {
    showToast: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

type ToastProviderProps = {
    children: ReactNode;
}

function ToastProvider({children}: ToastProviderProps) {
    const [message, setMessage] = useState<string | null>(null);
    const timeoutRef = useRef<number | null>(null);

    const value = useMemo<ToastContextValue>(() => ({
        showToast(nextMessage: string) {
            if (timeoutRef.current !== null) {
                window.clearTimeout(timeoutRef.current);
            }
            setMessage(nextMessage);
            timeoutRef.current = window.setTimeout(() => {
                setMessage(null);
                timeoutRef.current = null;
            }, 2600);
        }
    }), []);

    return (
        <ToastContext.Provider value={value}>
            {children}
            {message && (
                <div className={styles.toastHost} role="status" aria-live="polite">
                    <div className={styles.toast}>{message}</div>
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
