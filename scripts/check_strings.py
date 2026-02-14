#!/usr/bin/env python3
import os
import re
import sys
import xml.etree.ElementTree as ET

# Detection regex patterns
XML_ATTRS = [
    r'android:text',
    r'android:hint',
    r'app:title',
    r'app:subtitle',
    r'app:hintText',
    r'android:contentDescription',
    r'android:title',
    r'android:summary',
    r'android:label'
]

# Regex for XML: Matches the attribute and its value if it doesn't start with @string/ or @android:string/
XML_PATTERN = re.compile(r'(' + "|".join(XML_ATTRS) + r')\s*=\s*"((?!@string/|@android:string/).*?)"', re.IGNORECASE)

# Regex to find @string/ references in any XML file
STRING_REF_PATTERN = re.compile(r'@(?:string|android:string)/([a-zA-Z0-9_]+)')

# Regex for Kotlin: Matches common UI-related setters or method calls with hard-coded string literals
KOTLIN_PATTERNS = [
    re.compile(r'\.\s*(text|hint|title|subtitle|summary)\s*=\s*"(.+?)"'),
    re.compile(r'\.\s*(?:setText|setHint|setTitle|setSubtitle|setSummary)\s*\(\s*"(.+?)"\s*\)'),
    re.compile(r'(?:Toast\.makeText|Snackbar\.make)\s*\(\s*(?:[^,]+,\s*)*"(.+?)"\s*[,)]')
]

# Broad string literal search for Kotlin
KT_LITERAL_PATTERN = re.compile(r'"(.+?)"')

# Global state for definitions
DEFINED_STRINGS = set()
STRING_FILE_PATH = "app/src/main/res/values/strings.xml"

# Exclusions: Strings to ignore (technical, keys, etc.)
EXCLUSIONS = [
    r'^@null$',           # Decorative images
    r'^[a-z0-9_.]+$',    # Lowercase keys/packages
    r'^[A-Z0-9_.]+$',    # Uppercase constants
    r'^[a-zA-Z0-9_.-]+$', # Technical IDs
    r'^\d+$',            # Just numbers
    r'^$',               # Empty
    r'^.{1,2}$'          # Very short
]

# Exclusions for missing resources (known library strings)
LIB_EXCLUSIONS = {
    'appbar_scrolling_view_behavior',
    'character_counter_pattern',
    'bottom_sheet_behavior',
}

def is_excluded(val):
    val = val.strip()
    if not val:
        return True
    for p in EXCLUSIONS:
        if re.match(p, val):
            return True
    return False

def collect_defined_strings():
    strings_path = os.path.join(os.getcwd(), STRING_FILE_PATH)
    if not os.path.exists(strings_path):
        print(f"⚠️  Warning: {STRING_FILE_PATH} not found.")
        return
    
    try:
        tree = ET.parse(strings_path)
        root = tree.getroot()
        for string in root.findall('string'):
            name = string.get('name')
            if name:
                DEFINED_STRINGS.add(name)
    except Exception as e:
        print(f"Error parsing {STRING_FILE_PATH}: {e}")

def check_strings_xml_safety(strings_path):
    findings = []
    try:
        with open(strings_path, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f, 1):
                # We identify string content between tags
                content_match = re.search(r'>([^<]+)<', line)
                if content_match:
                    val = content_match.group(1)
                    # Unescaped ' check
                    if "'" in val and "\\'" not in val:
                        findings.append((i, "SAFETY", f"Unescaped apostrophe in: {val}", "CRITICAL"))
                    # Unescaped & check
                    if "&" in val and "&amp;" not in val:
                        if not re.search(r'&[a-z#0-9]+;', val):
                            findings.append((i, "SAFETY", f"Unescaped ampersand in: {val}", "CRITICAL"))
    except Exception as e:
        print(f"Error reading {strings_path}: {e}")
    return findings

def check_xml(file_path):
    findings = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f, 1):
                # 1. Hard-coded check
                hardcoded = XML_PATTERN.findall(line)
                for attr, val in hardcoded:
                    if not is_excluded(val):
                        findings.append((i, f"HARDCODED:{attr}", val, "HIGH"))
                
                # 2. Missing reference check
                refs = STRING_REF_PATTERN.findall(line)
                for ref in refs:
                    if ref not in DEFINED_STRINGS and not ref.startswith('android:') and ref not in LIB_EXCLUSIONS:
                        findings.append((i, "MISSING_REF", f"@string/{ref}", "HIGH"))
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    return findings

def check_kotlin(file_path):
    findings = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f, 1):
                clean_line = line.strip()
                if clean_line.startswith('import ') or clean_line.startswith('package ') or clean_line.startswith('Log.'):
                    continue
                
                # High-risk patterns
                for pattern in KOTLIN_PATTERNS:
                    matches = pattern.findall(line)
                    for match in matches:
                        val = match[1] if isinstance(match, tuple) else match
                        if not is_excluded(val):
                            findings.append((i, "KT_UI", val, "HIGH"))
                
                # Medium-risk literals
                literals = KT_LITERAL_PATTERN.findall(line)
                for lit in literals:
                    if not is_excluded(lit) and ' ' in lit:
                        if not any(f[2] == lit and f[0] == i for f in findings):
                            findings.append((i, "KT_TEXT", lit, "MEDIUM"))
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    return findings

def main():
    root_dir = os.path.join(os.getcwd(), 'app/src/main')
    if not os.path.exists(root_dir):
        print(f"Root directory {root_dir} not found. Run from the project root.")
        sys.exit(1)

    collect_defined_strings()
    
    all_findings = {}
    safety_findings = {}
    
    print(f"🚀 Starting Deep String Analysis in {root_dir}")

    # 1. Check strings.xml safety
    strings_path = os.path.join(os.getcwd(), STRING_FILE_PATH)
    if os.path.exists(strings_path):
        s_findings = check_strings_xml_safety(strings_path)
        if s_findings:
            safety_findings[STRING_FILE_PATH] = s_findings

    # 2. Project-wide scan
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            file_path = os.path.join(root, file)
            findings = []
            
            if file.endswith('.xml'):
                if 'strings.xml' not in file:
                    findings = check_xml(file_path)
            elif file.endswith('.kt'):
                findings = check_kotlin(file_path)

            if findings:
                rel_path = os.path.relpath(file_path, os.getcwd())
                all_findings[rel_path] = findings

    # Final Report
    if safety_findings:
        print("\n❌ CRITICAL BUILD SAFETY ISSUES:")
        for path, issues in safety_findings.items():
            for line, attr, val, risk in issues:
                print(f"  🔥 {path}:{line} - {val}")
        print("-" * 60)

    if not all_findings and not safety_findings:
        print("\n✨ All strings are localized, referenced correctly, and escaped. Awesome!")
    else:
        print(f"\n🚩 Project Scan Results ({len(all_findings)} files with issues):\n")
        total_counts = {"CRITICAL": 0, "HIGH": 0, "MEDIUM": 0}
        
        for path, issues in sorted(all_findings.items()):
            print(f"📄 {path}")
            for line, attr, val, risk in sorted(issues, key=lambda x: x[0]):
                icon = "🔴" if risk == "HIGH" else "🟡"
                print(f"  {icon} Line {line:4}: [{attr}] \"{val}\" (Risk: {risk})")
                total_counts[risk] += 1
            print("-" * 60)
        
        for issues in safety_findings.values():
            total_counts["CRITICAL"] += len(issues)

        print("\n📊 TOTAL CHECK RESULT:")
        print(f"  ❌ CRITICAL (Build Crash Risk): {total_counts['CRITICAL']}")
        print(f"  🔴 HIGH (Missing/Hardcoded):   {total_counts['HIGH']}")
        print(f"  🟡 MEDIUM (Potential Issues):  {total_counts['MEDIUM']}")

        if total_counts['CRITICAL'] > 0:
            print("\n⚠️  ACTION REQUIRED: Fix critical build safety issues immediately!")
        elif total_counts['HIGH'] > 0:
            print("\n💡 TIP: Fix missing resources and hard-coded strings for full i18n support.")

if __name__ == "__main__":
    main()