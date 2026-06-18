import type {InputHTMLAttributes} from "react";
import styles from "./TextInput.module.css";

type TextInputProps = InputHTMLAttributes<HTMLInputElement>;

function TextInput({ className = "", type = "text", ...props }: TextInputProps){
    return (
        <input
            {...props}
            type={type}
            className={`${styles.textInput} ${className}`.trim()}
        />
    );
}

export default TextInput;
