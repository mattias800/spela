import { Outlet } from "react-router-dom";
import { Gamepad2, Library, Heart, Clock, FolderOpen } from "lucide-react";
import { TabNav, TabItem } from "@/components/ui";

export function LibraryLayout() {
  return (
    <div>
      <TabNav className="mb-6">
        <TabItem to="/consoles" icon={Gamepad2} label="Consoles" />
        <TabItem to="/games" icon={Library} label="Games" />
        <TabItem to="/favorites" icon={Heart} label="Favorites" />
        <TabItem to="/play-later" icon={Clock} label="Play Later" />
        <TabItem to="/collections" icon={FolderOpen} label="Collections" />
      </TabNav>
      <Outlet />
    </div>
  );
}
