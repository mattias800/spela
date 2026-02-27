import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { Home, Library, Settings } from "lucide-react";
import { Sidebar } from "./sidebar";

const defaultLinks = [
  {
    items: [
      {
        to: "/",
        icon: Home,
        label: "Dashboard",
      },
      {
        to: "/library",
        icon: Library,
        label: "Library",
      },
    ],
  },
  {
    section: "Admin",
    items: [
      {
        to: "/admin/settings",
        icon: Settings,
        label: "Settings",
      },
    ],
  },
];

const defaultUser = { username: "testuser", role: "admin" };

function renderSidebar(props: Partial<Parameters<typeof Sidebar>[0]> = {}) {
  return render(
    <MemoryRouter>
      <Sidebar
        links={defaultLinks}
        user={defaultUser}
        onLogout={vi.fn()}
        {...props}
      />
    </MemoryRouter>,
  );
}

/** Helper to get the mobile drawer element (the aside with role="dialog"). */
function getDrawer() {
  return screen.getByRole("dialog", { name: "Navigation menu" });
}

/** Helper to get the backdrop (aria-hidden div inside the mobile wrapper). */
function getBackdrop(container: HTMLElement) {
  // The backdrop is the div with bg-black/50 inside the lg:hidden wrapper
  const mobileWrapper = container.querySelector("div.lg\\:hidden");
  return mobileWrapper?.querySelector("div[aria-hidden='true']") as HTMLElement | null;
}

describe("Sidebar", () => {
  beforeEach(() => {
    document.body.style.overflow = "";
  });

  describe("desktop mode", () => {
    it("renders sidebar with navigation links", () => {
      renderSidebar();
      // Both desktop sidebar and mobile drawer render the same content
      expect(screen.getAllByText("Dashboard")).toHaveLength(2);
      expect(screen.getAllByText("Library")).toHaveLength(2);
      expect(screen.getAllByText("Settings")).toHaveLength(2);
    });

    it("renders section titles", () => {
      renderSidebar();
      expect(screen.getAllByText("Admin")).toHaveLength(2);
    });

    it("renders user info", () => {
      renderSidebar();
      expect(screen.getAllByText("testuser")).toHaveLength(2);
      expect(screen.getAllByText("admin")).toHaveLength(2);
    });

    it("renders user avatar initial", () => {
      renderSidebar();
      expect(screen.getAllByText("T")).toHaveLength(2);
    });

    it("renders logout button", () => {
      renderSidebar();
      const logoutButtons = screen.getAllByLabelText("Logout");
      expect(logoutButtons).toHaveLength(2);
    });

    it("does not render user section when no user", () => {
      renderSidebar({ user: undefined });
      expect(screen.queryByText("testuser")).not.toBeInTheDocument();
    });

    it("does not render logout button when no onLogout", () => {
      renderSidebar({ onLogout: undefined });
      expect(screen.queryByLabelText("Logout")).not.toBeInTheDocument();
    });

    it("renders Spela branding", () => {
      renderSidebar();
      expect(screen.getAllByText("Spela")).toHaveLength(2);
    });
  });

  describe("mobile drawer", () => {
    it("renders drawer with dialog role", () => {
      renderSidebar({ mobileOpen: true });
      const dialog = getDrawer();
      expect(dialog).toBeInTheDocument();
    });

    it("sets aria-modal when open", () => {
      renderSidebar({ mobileOpen: true });
      const dialog = getDrawer();
      expect(dialog).toHaveAttribute("aria-modal", "true");
    });

    it("does not set aria-modal when closed", () => {
      renderSidebar({ mobileOpen: false });
      const dialog = getDrawer();
      expect(dialog).not.toHaveAttribute("aria-modal");
    });

    it("applies translate-x-0 class when open", () => {
      renderSidebar({ mobileOpen: true });
      const dialog = getDrawer();
      expect(dialog.className).toContain("translate-x-0");
    });

    it("applies -translate-x-full class when closed", () => {
      renderSidebar({ mobileOpen: false });
      const dialog = getDrawer();
      expect(dialog.className).toContain("-translate-x-full");
    });

    it("shows backdrop with full opacity when open", () => {
      const { container } = renderSidebar({ mobileOpen: true });
      const backdrop = getBackdrop(container);
      expect(backdrop).not.toBeNull();
      expect(backdrop!.className).toContain("opacity-100");
    });

    it("hides backdrop when closed", () => {
      const { container } = renderSidebar({ mobileOpen: false });
      const backdrop = getBackdrop(container);
      expect(backdrop).not.toBeNull();
      expect(backdrop!.className).toContain("opacity-0");
      expect(backdrop!.className).toContain("pointer-events-none");
    });

    it("calls onMobileClose when backdrop is clicked", async () => {
      const onClose = vi.fn();
      const { container } = renderSidebar({
        mobileOpen: true,
        onMobileClose: onClose,
      });
      const backdrop = getBackdrop(container);
      await userEvent.click(backdrop!);
      expect(onClose).toHaveBeenCalledOnce();
    });

    it("calls onMobileClose when Escape key is pressed", async () => {
      const onClose = vi.fn();
      renderSidebar({ mobileOpen: true, onMobileClose: onClose });
      await userEvent.keyboard("{Escape}");
      expect(onClose).toHaveBeenCalledOnce();
    });

    it("does not call onMobileClose on Escape when drawer is closed", async () => {
      const onClose = vi.fn();
      renderSidebar({ mobileOpen: false, onMobileClose: onClose });
      await userEvent.keyboard("{Escape}");
      expect(onClose).not.toHaveBeenCalled();
    });

    it("locks body scroll when open", () => {
      renderSidebar({ mobileOpen: true });
      expect(document.body.style.overflow).toBe("hidden");
    });

    it("unlocks body scroll when closed", () => {
      renderSidebar({ mobileOpen: false });
      expect(document.body.style.overflow).toBe("");
    });

    it("restores body scroll on unmount", () => {
      document.body.style.overflow = "hidden";
      const { unmount } = renderSidebar({ mobileOpen: true });
      expect(document.body.style.overflow).toBe("hidden");
      unmount();
      expect(document.body.style.overflow).toBe("");
    });

    it("has shadow-2xl class on drawer", () => {
      renderSidebar({ mobileOpen: true });
      const dialog = getDrawer();
      expect(dialog.className).toContain("shadow-2xl");
    });
  });
});
