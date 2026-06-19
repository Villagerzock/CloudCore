import styles from "./UserIcon.module.css";
import {FaUser} from "react-icons/fa";
import {useEffect, useRef, useState} from "react";
import Button from "./Button.tsx";
import {logout} from "../lib/api.ts";


function UserIcon(){
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
            window.alert(error instanceof Error ? error.message : "Logout failed");
        } finally {
            window.location.assign("/login");
        }
    }

    return (
        <>
            <div ref={menuRef} className={styles.menu}>
                {open && (
                    <div className={styles.popup}>
                        <Button type={"clear"} onClick={()=>{}}>
                            My Account
                        </Button>
                        <hr/>
                        <Button type={"clear"} onClick={handleLogout}>Logout</Button>
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
