export interface ParsedEnumType {
  baseType: string;
  pairs: Array<[string, string]>;
}

// Parse Enum type to extract base type and key-value pairs
export function parseEnumType(typeString: string): ParsedEnumType | null {
  const type = String(typeString || "").trim();

  // Match Enum8, Enum16, Enum, etc.
  const enumMatch = type.match(/^(Enum\d*)\s*\((.+)\)$/);
  if (!enumMatch) {
    return null;
  }

  const baseType = enumMatch[1];
  const content = enumMatch[2];
  const pairs: Array<[string, string]> = [];

  // Parse key-value pairs: 'NewPart' = 1, 'MergeParts' = 2
  const pairRegex = /'([^']+)'\s*=\s*(\d+)/g;
  let match: RegExpExecArray | null = null;
  while ((match = pairRegex.exec(content)) !== null) {
    const key = match[1];
    const value = match[2];
    pairs.push([key, value]);
  }

  return { baseType, pairs };
}
