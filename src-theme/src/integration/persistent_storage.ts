import { getPersistentStorageItems, setPersistentStorageItems } from "./rest";
import type { PersistentStorageItem } from "./types";

let loadedOnce = false;
let persistentDataUpdateTimeout: null | number = null;

function collectPersistentStorageItems(): PersistentStorageItem[] {
    const items: PersistentStorageItem[] = [];

    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)!!;
        const value = localStorage.getItem(key)!!;

        items.push({
            key,
            value
        });
    }

    return items;
}

export async function insertPersistentData() {
    const items = await getPersistentStorageItems();

    for (const { key, value } of items) {
        localStorage.setItem(key, value);
    }
    loadedOnce = true;
}

export async function updatePersistentData() {
    if (persistentDataUpdateTimeout !== null) {
        clearTimeout(persistentDataUpdateTimeout);
    }

    persistentDataUpdateTimeout = setTimeout(async () => {
        persistentDataUpdateTimeout = null;

        if (!loadedOnce) {
            return;
        }

        await setPersistentStorageItems(collectPersistentStorageItems());
    }, 200);
}

export async function setItem(name: string, value: string) {
    localStorage.setItem(name ,value);
    await updatePersistentData();
}

export async function removeItem(name: string) {
    localStorage.removeItem(name);
    await flushPersistentData();
}

export async function flushPersistentData() {
    if (persistentDataUpdateTimeout !== null) {
        clearTimeout(persistentDataUpdateTimeout);
        persistentDataUpdateTimeout = null;
    }

    if (!loadedOnce) {
        return;
    }

    await setPersistentStorageItems(collectPersistentStorageItems());
}
