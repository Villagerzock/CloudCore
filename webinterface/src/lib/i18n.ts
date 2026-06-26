import {useEffect, useMemo, useState} from "react";

type Translations = Record<string, string>;
type DefaultLanguages = Record<string, string>;

const FALLBACK_LANGUAGE = "en-US";
const LOCALE_PATTERN = /^[a-z]{2}-[A-Z]{2}$/;
const cache = new Map<string, Translations | null>();
let defaultLanguagesPromise: Promise<DefaultLanguages> | null = null;
let activeBundles: Translations[] = [];
let activeLanguage = FALLBACK_LANGUAGE;
let loadPromise: Promise<void> | null = null;

function fallbackLabel(key: string): string {
    const lastPart = key.split(".").at(-1) ?? key;
    return lastPart
        .split("_")
        .map(part => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
}

async function loadDefaultLanguages(): Promise<DefaultLanguages> {
    defaultLanguagesPromise ??= fetch("/i18n/default.json", {
        headers: {Accept: "application/json"}
    })
        .then(response => response.ok ? response.json() as Promise<DefaultLanguages> : {})
        .catch(() => ({}));

    return defaultLanguagesPromise;
}

async function languageCandidates(): Promise<string[]> {
    const defaultLanguages = await loadDefaultLanguages();
    const browserLanguages = typeof navigator === "undefined"
        ? []
        : navigator.languages.length > 0
            ? navigator.languages
            : [navigator.language];

    const candidates = [
        ...browserLanguages,
        FALLBACK_LANGUAGE
    ].map(language => {
        const normalized = normalizeLanguage(language);
        if (normalized === null) return null;
        if (LOCALE_PATTERN.test(normalized)) return normalized;
        return defaultLanguages[normalized] ?? null;
    }).filter((language): language is string => {
        return language !== null && LOCALE_PATTERN.test(language);
    });

    return [...new Set(candidates)];
}

function normalizeLanguage(language: string): string | null {
    const trimmed = language.trim();
    if (trimmed.length === 0) return null;

    const [rawLanguage, rawCountry] = trimmed.replace("_", "-").split("-");
    const normalizedLanguage = rawLanguage.toLowerCase();
    if (!/^[a-z]{2}$/.test(normalizedLanguage)) return null;
    if (rawCountry === undefined) return normalizedLanguage;

    const normalizedCountry = rawCountry.toUpperCase();
    if (!/^[A-Z]{2}$/.test(normalizedCountry)) return null;
    return `${normalizedLanguage}-${normalizedCountry}`;
}

async function loadLanguage(language: string): Promise<Translations | null> {
    if (cache.has(language)) {
        return cache.get(language) ?? null;
    }

    try {
        const response = await fetch(`/i18n/${encodeURIComponent(language)}.json`, {
            headers: {Accept: "application/json"}
        });
        if (!response.ok) {
            cache.set(language, null);
            return null;
        }
        const translations = await response.json() as Translations;
        cache.set(language, translations);
        return translations;
    } catch {
        cache.set(language, null);
        return null;
    }
}

async function loadPreferredTranslations(): Promise<void> {
    const bundles: Translations[] = [];
    let selectedLanguage = FALLBACK_LANGUAGE;

    for (const language of await languageCandidates()) {
        const translations = await loadLanguage(language);
        if (translations === null) continue;
        if (bundles.length === 0) {
            selectedLanguage = language;
        }
        bundles.push(translations);
    }

    activeBundles = bundles;
    activeLanguage = selectedLanguage;
}

export function t(key: string): string {
    for (const bundle of activeBundles) {
        const translation = bundle[key];
        if (translation !== undefined) {
            return translation;
        }
    }
    return fallbackLabel(key);
}

export function useI18n() {
    const [version, setVersion] = useState(0);

    useEffect(() => {
        let cancelled = false;
        loadPromise ??= loadPreferredTranslations();
        loadPromise.then(() => {
            if (!cancelled) {
                setVersion(current => current + 1);
            }
        });
        return () => {
            cancelled = true;
        };
    }, []);

    return useMemo(() => ({
        language: activeLanguage,
        t
    }), [version]);
}
