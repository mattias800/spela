/**
 * Safely extract the year from a release date string.
 * Returns null if the date is invalid or missing.
 * Handles ISO dates like "1993-08-01" and also bare years like "1993".
 * Pouet sometimes returns dates like "1989-00-15" which are invalid.
 */
export function getReleaseYear(
  releaseDate: string | null | undefined,
): number | null {
  if (!releaseDate) return null;

  // Try extracting year directly from the string (works for "YYYY" and "YYYY-MM-DD")
  const match = releaseDate.match(/^(\d{4})/);
  if (!match) return null;

  const year = parseInt(match[1], 10);
  if (isNaN(year) || year < 1900 || year > 2100) return null;

  return year;
}
