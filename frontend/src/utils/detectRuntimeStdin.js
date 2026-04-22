/**
 * Heuristic: whether the source likely reads runtime stdin (interactive / piped input).
 * Not exhaustive; avoids treating all programs as stdin-driven.
 *
 * @param {string} code
 * @param {string} language raw form language (e.g. python, java, cpp)
 * @returns {boolean}
 */
export function detectNeedsRuntimeStdin(code, language) {
  const src = String(code || "");
  if (!src.trim()) return false;

  const lang = String(language || "").toLowerCase().replace(/\s+/g, "");

  const py = /\binput\s*\(|sys\.stdin\b/;
  const java =
    /Scanner\s*\(\s*System\.in|BufferedReader|InputStreamReader\s*\(\s*System\.in|System\.console\s*\(\s*\)\s*\.readLine|System\.in\.read\s*\(/;
  const cpp =
    /\bcin\s*>>|::\s*cin\s*>>|std\s*::\s*cin\b|getline\s*\(\s*(?:std\s*::\s*)?cin\b|std\s*::\s*getline\s*\(\s*std\s*::\s*cin/;
  const cOnly = /\bscanf\s*\(|\bgetchar\s*\(|\bfgets\s*\(\s*stdin/;
  const goRx = /\bfmt\.Scan(?:f|ln)?\s*\(|bufio\.NewScanner\s*\(\s*os\.Stdin|\.ReadString\s*\(|bufio\.NewReader\s*\(\s*os\.Stdin/;

  if (lang === "python" || lang === "py") return py.test(src);
  if (lang === "java") return java.test(src);
  if (lang === "cpp" || lang === "c++" || lang === "cplusplus") return cpp.test(src) || /\bscanf\s*\(/.test(src);
  if (lang === "c") return cOnly.test(src);
  if (lang === "go" || lang === "golang") return goRx.test(src);

  return py.test(src) || java.test(src) || cpp.test(src) || cOnly.test(src) || goRx.test(src);
}
