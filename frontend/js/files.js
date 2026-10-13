/*
 * Reads the files a visitor picks or drops, inside the browser.
 *
 * Nothing is uploaded: the text goes straight into the browser engine. Like
 * the Java DocumentLoader, it reads every .txt file, including the ones in
 * sub folders, and counts the files it could not read.
 *
 * Exposed as window.Files.
 */
window.Files = (function () {
  "use strict";

  const MAX_FILE_BYTES = 5 * 1024 * 1024; // larger .txt files are skipped, so the tab stays responsive
  const MAX_FILES = 5000;

  const isText = (name) => name.toLowerCase().endsWith(".txt");
  const compare = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

  // A FileList from <input type="file">. A folder picker fills in webkitRelativePath ("notes/java.txt").
  function fromInput(fileList) {
    return Array.from(fileList || []).map((file) => ({ file, path: file.webkitRelativePath || file.name }));
  }

  // A drop. Must be called straight from the drop handler: the browser clears the items once it returns.
  function fromDrop(dataTransfer) {
    const entries = Array.from(dataTransfer.items || [])
      .map((item) => (item.webkitGetAsEntry ? item.webkitGetAsEntry() : null))
      .filter(Boolean);
    if (!entries.length) return Promise.resolve(fromInput(dataTransfer.files));

    return (async () => {
      const found = [];
      for (const entry of entries) await walk(entry, found);
      return found;
    })();
  }

  // Visits a dropped entry: a file is collected, a folder is visited one level down (recursion).
  async function walk(entry, found) {
    if (entry.isFile) {
      try {
        const file = await new Promise((resolve, reject) => entry.file(resolve, reject));
        found.push({ file, path: entry.fullPath.replace(/^\//, "") });
      } catch (e) {
        found.push({ file: null, path: entry.fullPath.replace(/^\//, "") });
      }
      return;
    }
    if (!entry.isDirectory) return;

    const reader = entry.createReader();
    let batch;
    do {
      // readEntries hands back at most about 100 entries at a time, so keep asking until it is empty.
      batch = await new Promise((resolve, reject) => reader.readEntries(resolve, reject));
      for (const child of batch) await walk(child, found);
    } while (batch.length);
  }

  // The folder name when everything picked sits in one folder, otherwise "" (loose files have no folder).
  function labelFor(list) {
    const firstParts = list.map((x) => x.path.split("/")[0]);
    const shared = list.length > 0 && list.every((x) => x.path.indexOf("/") > 0) && firstParts.every((p) => p === firstParts[0]);
    return shared ? firstParts[0] : "";
  }

  /**
   * Reads the text of every .txt file in the list.
   * Returns { docs, label, skipped, ignored }:
   *   docs     [{ title, path, text }] ready for Engine.build
   *   label    the folder name, or "" for loose files
   *   skipped  .txt files that could not be read, or were too large
   *   ignored  files that are not .txt
   */
  async function read(list, onProgress) {
    const wanted = list.filter((x) => isText(x.path)).sort((a, b) => compare(a.path, b.path));
    const docs = [];
    let skipped = 0;

    for (let i = 0; i < wanted.length; i++) {
      const { file, path } = wanted[i];
      if (!file || file.size > MAX_FILE_BYTES || docs.length >= MAX_FILES) {
        skipped++;
        continue;
      }
      try {
        docs.push({ title: file.name, path, text: await file.text() });
      } catch (e) {
        skipped++;
      }
      if (onProgress && (i % 25 === 0 || i === wanted.length - 1)) onProgress(i + 1, wanted.length);
    }

    return { docs, label: labelFor(list), skipped, ignored: list.length - wanted.length };
  }

  return { fromInput, fromDrop, read };
})();
