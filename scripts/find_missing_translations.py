#!/usr/bin/env python3
import os
import xml.etree.ElementTree as ET

def get_keys(xml_path):
    if not os.path.exists(xml_path):
        return set()
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        keys = set()
        for string in root.findall('string'):
            name = string.get('name')
            if name:
                keys.add(name)
        for plural in root.findall('plurals'):
            name = plural.get('name')
            if name:
                keys.add(name)
        return keys
    except Exception as e:
        print(f"Error parsing {xml_path}: {e}")
        return set()

def main():
    res_dir = "app/src/main/res"
    base_strings = os.path.join(res_dir, "values/strings.xml")
    
    if not os.path.exists(base_strings):
        print(f"Base strings not found at {base_strings}")
        return

    base_keys = get_keys(base_strings)
    print(f"Base strings (English): {len(base_keys)} keys found.")

    for folder in os.listdir(res_dir):
        if folder.startswith("values-") and folder != "values-night":
            lang_strings = os.path.join(res_dir, folder, "strings.xml")
            if os.path.exists(lang_strings):
                lang_keys = get_keys(lang_strings)
                missing = base_keys - lang_keys
                if missing:
                    print(f"\n🌍 {folder}: {len(missing)} missing keys")
                    for key in sorted(missing):
                        print(f"  - {key}")
                else:
                    print(f"\n✅ {folder}: All keys translated.")

if __name__ == "__main__":
    main()
