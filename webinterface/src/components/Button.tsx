import styles from "./Button.module.css"
import * as React from "react";
import type {MouseEventHandler} from "react";

type ButtonProps = {
    type: "clear" | "primary" | "secondary" | "danger";
    children: React.ReactNode;
    onClick: MouseEventHandler<HTMLButtonElement>;
}

function Button({ type, children, onClick }: ButtonProps){
    return (
        <button className={`${styles.button} ${type}`} onClick={onClick}>
            {children}
        </button>
    )
}

export default Button;