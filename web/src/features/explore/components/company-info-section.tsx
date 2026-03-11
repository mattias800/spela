import { useState } from "react";
import { Globe, BookOpen, ChevronDown, ChevronUp } from "lucide-react";
import type { CompanyInfo } from "@/types/api";

interface CompanyInfoSectionProps {
  companyInfo: CompanyInfo;
}

export function CompanyInfoSection({ companyInfo }: CompanyInfoSectionProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const hasDescription = !!companyInfo.description;
  const hasMetadata = !!companyInfo.foundedYear || !!companyInfo.country;
  const hasLinks = !!companyInfo.websiteUrl || !!companyInfo.wikipediaUrl;

  if (!hasDescription && !hasMetadata && !hasLinks) {
    return null;
  }

  return (
    <section data-testid="company-info-section" className="space-y-3">
      {/* Description */}
      {hasDescription && (
        <div data-testid="company-description">
          <div
            className={`text-sm text-surface-300 leading-relaxed ${
              !isExpanded ? "line-clamp-3" : ""
            }`}
            data-testid="company-description-text"
          >
            {companyInfo.description}
          </div>
          {/* Only show toggle if description is long enough to be clamped */}
          {companyInfo.description!.length > 200 && (
            <button
              type="button"
              onClick={() => setIsExpanded(!isExpanded)}
              className="mt-1 flex items-center gap-1 text-xs font-medium text-primary-400 hover:text-primary-300 transition-colors"
              data-testid="company-description-toggle"
            >
              {isExpanded ? (
                <>
                  Show less <ChevronUp className="h-3 w-3" />
                </>
              ) : (
                <>
                  Show more <ChevronDown className="h-3 w-3" />
                </>
              )}
            </button>
          )}
        </div>
      )}

      {/* Metadata line */}
      {hasMetadata && (
        <p
          className="text-xs text-surface-400"
          data-testid="company-metadata"
        >
          {companyInfo.foundedYear && companyInfo.country
            ? `Founded ${companyInfo.foundedYear} \u2014 ${companyInfo.country}`
            : companyInfo.foundedYear
              ? `Founded ${companyInfo.foundedYear}`
              : companyInfo.country}
        </p>
      )}

      {/* Links */}
      {hasLinks && (
        <div
          className="flex items-center gap-3"
          data-testid="company-links"
        >
          {companyInfo.websiteUrl && (
            <a
              href={companyInfo.websiteUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-xs text-surface-400 hover:text-surface-200 transition-colors"
              data-testid="company-website-link"
            >
              <Globe className="h-3.5 w-3.5" />
              Website
            </a>
          )}
          {companyInfo.wikipediaUrl && (
            <a
              href={companyInfo.wikipediaUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-xs text-surface-400 hover:text-surface-200 transition-colors"
              data-testid="company-wikipedia-link"
            >
              <BookOpen className="h-3.5 w-3.5" />
              Wikipedia
            </a>
          )}
        </div>
      )}
    </section>
  );
}
