import {type JSX, type ReactNode} from "react";
import styles from "./Header.module.css"
import UserIcon from "./UserIcon.tsx";

type HeaderProps = {
    children?: ReactNode;
}

function Header({ children }: HeaderProps) : JSX.Element {
    return (
        <div className={styles.header}>
            <div className={styles.left}>
                {children}
            </div>
            <div className={styles.right}>
                <UserIcon/>
            </div>
        </div>
    );
}

export default Header;
