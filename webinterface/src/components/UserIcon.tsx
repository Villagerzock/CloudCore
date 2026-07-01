import styles from "./UserIcon.module.css";
import {FaUser} from "react-icons/fa";
import {useEffect, useRef, useState} from "react";
import Button from "./Button.tsx";
import {logout, usePersistentNavigate} from "../lib/api.ts";
import {useI18n} from "../lib/i18n.ts";
import {useToast} from "./ToastProvider.tsx";
import {errorMessage} from "../lib/errors.ts";


function UserIcon(){
    const {t} = useI18n();
    const {showError} = useToast();
    const navigate = usePersistentNavigate();
    const [open, setOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;

        function closeOnOutsideClick(event: PointerEvent) {
            if (!menuRef.current?.contains(event.target as Node)) {
                setOpen(false);
            }
        }

        document.addEventListener("pointerdown", closeOnOutsideClick);
        return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
    }, [open]);

    async function handleLogout() {
        try {
            await logout();
        } catch (error) {
            showError(errorMessage(error, "Logout failed"));
        } finally {
            window.location.assign("/login");
        }
    }

    return (
        <>
            <div ref={menuRef} className={styles.menu}>
                {open && (
                    <div className={styles.popup}>
                        <Button type={"clear"} onClick={() => {
                            setOpen(false);
                            navigate("/account");
                        }}>
                            {t("account.my_account")}
                        </Button>
                        <hr/>
                        <Button type={"clear"} onClick={handleLogout}>{t("account.logout")}</Button>
                    </div>
                )}
                <div className={styles.circle} onClick={()=> setOpen(!open)}>
                    <FaUser size={"100%"}/>
                </div>
            </div>
        </>
    )
}

export default UserIcon;
