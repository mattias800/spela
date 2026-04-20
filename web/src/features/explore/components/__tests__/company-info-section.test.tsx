import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import { CompanyInfoSection } from "@/features/explore/components/company-info-section";
import type { CompanyInfo } from "@/types/api";
import { makeCompanyInfo } from "@/test-utils/fixtures";

const fullCompanyInfo: CompanyInfo = {
  logoUrl: "/images/companies/capcom-logo.png",
  description:
    "Capcom Co., Ltd. is a Japanese video game developer and publisher known for creating multi-million-selling game franchises including Street Fighter, Mega Man, Resident Evil, Devil May Cry, Monster Hunter, and Ace Attorney. Founded in 1979, the company is headquartered in Osaka, Japan and has become one of the most recognized names in gaming worldwide with a reputation for high-quality action games.",
  foundedYear: 1979,
  country: "Japan",
  websiteUrl: "https://www.capcom.com",
  wikipediaUrl: "https://en.wikipedia.org/wiki/Capcom",
};

describe("CompanyInfoSection", () => {
  it("renders the section when company info has content", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    expect(screen.getByTestId("company-info-section")).toBeInTheDocument();
  });

  it("renders nothing when company info has no meaningful data", () => {
    const { container } = render(
      <CompanyInfoSection companyInfo={makeCompanyInfo({ logoUrl: "/logo.png" })} />,
    );
    expect(container.firstChild).toBeNull();
  });

  // --- Description ---

  it("renders description text", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    expect(screen.getByTestId("company-description")).toBeInTheDocument();
    expect(screen.getByTestId("company-description-text")).toHaveTextContent(
      "Capcom Co., Ltd.",
    );
  });

  it("shows description collapsed by default with line-clamp", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    const text = screen.getByTestId("company-description-text");
    expect(text.className).toContain("line-clamp-3");
  });

  it("expands description when 'Show more' is clicked", async () => {
    const user = userEvent.setup();
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);

    const toggle = screen.getByTestId("company-description-toggle");
    expect(toggle).toHaveTextContent("Show more");

    await user.click(toggle);

    const text = screen.getByTestId("company-description-text");
    expect(text.className).not.toContain("line-clamp-3");
    expect(toggle).toHaveTextContent("Show less");
  });

  it("collapses description when 'Show less' is clicked", async () => {
    const user = userEvent.setup();
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);

    const toggle = screen.getByTestId("company-description-toggle");
    await user.click(toggle); // expand
    await user.click(toggle); // collapse

    const text = screen.getByTestId("company-description-text");
    expect(text.className).toContain("line-clamp-3");
    expect(toggle).toHaveTextContent("Show more");
  });

  it("hides toggle for short descriptions", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ description: "A short description." })}
      />,
    );
    expect(screen.getByTestId("company-description")).toBeInTheDocument();
    expect(
      screen.queryByTestId("company-description-toggle"),
    ).not.toBeInTheDocument();
  });

  it("hides description section when no description", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ foundedYear: 1979, country: "Japan" })}
      />,
    );
    expect(
      screen.queryByTestId("company-description"),
    ).not.toBeInTheDocument();
  });

  // --- Metadata ---

  it("renders founded year and country", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    const meta = screen.getByTestId("company-metadata");
    expect(meta).toHaveTextContent("Founded 1979 \u2014 Japan");
  });

  it("renders only founded year when country is missing", () => {
    render(
      <CompanyInfoSection companyInfo={makeCompanyInfo({ foundedYear: 1985 })} />,
    );
    const meta = screen.getByTestId("company-metadata");
    expect(meta).toHaveTextContent("Founded 1985");
    expect(meta).not.toHaveTextContent("\u2014");
  });

  it("renders only country when founded year is missing", () => {
    render(
      <CompanyInfoSection companyInfo={makeCompanyInfo({ country: "Japan" })} />,
    );
    const meta = screen.getByTestId("company-metadata");
    expect(meta).toHaveTextContent("Japan");
    expect(meta).not.toHaveTextContent("Founded");
  });

  it("hides metadata when neither year nor country is present", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ description: "Some description that is long enough to be useful." })}
      />,
    );
    expect(
      screen.queryByTestId("company-metadata"),
    ).not.toBeInTheDocument();
  });

  // --- Links ---

  it("renders website link", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    const link = screen.getByTestId("company-website-link");
    expect(link).toHaveAttribute("href", "https://www.capcom.com");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveTextContent("Website");
  });

  it("renders wikipedia link", () => {
    render(<CompanyInfoSection companyInfo={fullCompanyInfo} />);
    const link = screen.getByTestId("company-wikipedia-link");
    expect(link).toHaveAttribute(
      "href",
      "https://en.wikipedia.org/wiki/Capcom",
    );
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveTextContent("Wikipedia");
  });

  it("hides links section when no URLs provided", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ foundedYear: 1979, country: "Japan" })}
      />,
    );
    expect(screen.queryByTestId("company-links")).not.toBeInTheDocument();
  });

  it("renders only website link when wikipedia is missing", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ websiteUrl: "https://example.com" })}
      />,
    );
    expect(screen.getByTestId("company-website-link")).toBeInTheDocument();
    expect(
      screen.queryByTestId("company-wikipedia-link"),
    ).not.toBeInTheDocument();
  });

  it("renders only wikipedia link when website is missing", () => {
    render(
      <CompanyInfoSection
        companyInfo={makeCompanyInfo({ wikipediaUrl: "https://en.wikipedia.org/wiki/Test" })}
      />,
    );
    expect(
      screen.queryByTestId("company-website-link"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("company-wikipedia-link")).toBeInTheDocument();
  });
});
