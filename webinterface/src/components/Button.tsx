import styles from "./Button.module.css"
import * as React from "react";
import type {ButtonHTMLAttributes} from "react";

type ButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "type"> & {
    type: "clear" | "primary" | "secondary" | "danger";
    buttonType?: "button" | "submit" | "reset";
    children: React.ReactNode;
}

function Button({ type, buttonType = "button", children, className = "", ...props }: ButtonProps){
    return (
        <button className={`${styles.button} ${styles[type]} ${className}`} type={buttonType} {...props}>
            {children}
        </button>
    )
}

export default Button;
