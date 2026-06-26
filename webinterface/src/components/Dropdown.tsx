import {useEffect, useRef, useState} from "react";
import {FaChevronDown} from "react-icons/fa";
import styles from "./Dropdown.module.css";

export type DropdownOption<T extends string | number> = {
    value: T;
    label: string;
}

type DropdownProps<T extends string | number> = {
    id: string;
    value: T | null;
    options: DropdownOption<T>[];
    onChange: (value: T) => void;
}

function Dropdown<T extends string | number>({id, value, options, onChange}: DropdownProps<T>) {
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement | null>(null);
    const selected = options.find(option => option.value === value);

    useEffect(() => {
        function closeOnOutsideClick(event: MouseEvent) {
            if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
                setOpen(false);
            }
        }

        document.addEventListener("mousedown", closeOnOutsideClick);
        return () => document.removeEventListener("mousedown", closeOnOutsideClick);
    }, []);

    function choose(nextValue: T) {
        onChange(nextValue);
        setOpen(false);
    }

    return (
        <div className={styles.dropdown} ref={rootRef}>
            <button
                id={id}
                className={styles.trigger}
                type="button"
                aria-haspopup="listbox"
                aria-expanded={open}
                onClick={() => setOpen(current => !current)}
            >
                <span>{selected?.label ?? "Select"}</span>
                <FaChevronDown className={`${styles.chevron} ${open ? styles.chevronOpen : ""}`} aria-hidden/>
            </button>
            {open && (
                <div className={styles.menu} role="listbox" aria-labelledby={id}>
                    {options.map(option => (
                        <button
                            key={String(option.value)}
                            type="button"
                            role="option"
                            aria-selected={option.value === value}
                            className={`${styles.option} ${option.value === value ? styles.optionActive : ""}`}
                            onClick={() => choose(option.value)}
                        >
                            {option.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

export default Dropdown;
