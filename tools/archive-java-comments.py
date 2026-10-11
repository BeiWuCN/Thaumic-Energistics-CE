from pathlib import Path
import argparse
import re


def comments(source):
    i = 0
    while i < len(source):
        if source.startswith('"""', i):
            i += 3
            while i < len(source):
                if source[i] == "\\":
                    i += 2
                elif source.startswith('"""', i):
                    i += 3
                    break
                else:
                    i += 1
        elif source[i] in ('"', "'"):
            quote = source[i]
            i += 1
            while i < len(source):
                if source[i] == "\\":
                    i += 2
                elif source[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
        elif source.startswith("//", i):
            end = source.find("\n", i)
            end = len(source) if end < 0 else end
            yield i, end, source[i:end]
            i = end
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            if end < 0:
                raise ValueError("Unclosed block comment")
            end += 2
            yield i, end, source[i:end]
            i = end
        else:
            i += 1


def strip(source, spans):
    result = []
    position = 0
    affected = set()
    for start, end, content in spans:
        result.append(source[position:start])
        result.append("".join("\n" if c == "\n" else " " for c in content))
        first = source.count("\n", 0, start)
        affected.update(range(first, first + content.count("\n") + 1))
        position = end
    result.append(source[position:])
    lines = "".join(result).splitlines(keepends=True)
    return "".join(line.rstrip(" \t\r\n") + ("\n" if line.endswith("\n") else "")
                   if index in affected else line
                   for index, line in enumerate(lines)
                   if index not in affected or line.strip())


def selfcheck():
    source = 'class A { String url = "https://x/*keep*/"; char c = \'/\'; /*gone*/ int x; //gone\n' + 'String t = """\n//keep\n/*keep*/\n"""; }'
    spans = list(comments(source))
    assert len(spans) == 2
    cleaned = strip(source, spans)
    assert 'https://x/*keep*/' in cleaned and '//keep\n/*keep*/' in cleaned
    assert not list(comments(cleaned))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    selfcheck()
    root = Path(__file__).resolve().parent.parent
    files = sorted((root / "src").rglob("*.java"))
    archive = root / "doc/code-comments"
    rows = []
    count = 0
    for file in files:
        source = file.read_text(encoding="utf-8")
        spans = list(comments(source))
        if not spans:
            continue
        if args.check:
            print(f"Remaining comments: {file.relative_to(root)} ({len(spans)})")
            count += len(spans)
            continue
        relative = file.relative_to(root)
        target = archive / (relative.as_posix() + ".md")
        if target.exists():
            raise ValueError(f"Archive already exists: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        content = [f"# `{relative.as_posix()}`\n", "以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。\n"]
        for start, end, text in spans:
            line = source.count("\n", 0, start) + 1
            content.extend([f"## 原第 {line} 行\n", "````java\n" + text + "\n````\n"])
        target.write_text("\n".join(content), encoding="utf-8")
        cleaned = strip(source, spans)
        assert not list(comments(cleaned)), relative
        file.write_text(cleaned, encoding="utf-8")
        rows.append(f"- [{relative.as_posix()}]({relative.as_posix()}.md)：{len(spans)} 条")
        count += len(spans)
    if args.check:
        print(f"Checked {len(files)} Java files; remaining comments: {count}")
        raise SystemExit(1 if count else 0)
    archive.mkdir(parents=True, exist_ok=True)
    header = f"# 源码注释归档\n\n版本：2.7.4.4。共 {len(rows)} 个文件、{count} 条注释。\n\n归档包含 `src` 下 Java 源码的说明注释、行内注释及 Javadoc，按原文件路径组织。原始行号用于对应迁移前的源码；说明原文未改写。字符串、字符常量和文本块保持原样。\n\n此目录不属于源码或资源集，不加入发布 JAR 和源码 JAR。后续说明集中维护在对应文档中。使用 `python tools/archive-java-comments.py --check` 可检查源码是否仍有注释。\n\n"
    (archive / "README.md").write_text(header + "\n".join(rows) + "\n", encoding="utf-8")
    print(f"Archived {count} comments from {len(rows)} files; checked {len(files)} Java files")


if __name__ == "__main__":
    main()
