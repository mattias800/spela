import { Routes, Route } from "react-router-dom";
import { Gallery } from "./Gallery";

// Proposals will be lazily imported
import { Proposal1 } from "./proposals/proposal-1";
import { Proposal2 } from "./proposals/proposal-2";
import { Proposal3 } from "./proposals/proposal-3";
import { Proposal4 } from "./proposals/proposal-4";
import { Proposal5 } from "./proposals/proposal-5";
import { Proposal6 } from "./proposals/proposal-6";
import { Proposal7 } from "./proposals/proposal-7";
import { Proposal8 } from "./proposals/proposal-8";
import { Proposal9 } from "./proposals/proposal-9";
import { Proposal10 } from "./proposals/proposal-10";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Gallery />} />
      <Route path="/proposal/1/*" element={<Proposal1 />} />
      <Route path="/proposal/2/*" element={<Proposal2 />} />
      <Route path="/proposal/3/*" element={<Proposal3 />} />
      <Route path="/proposal/4/*" element={<Proposal4 />} />
      <Route path="/proposal/5/*" element={<Proposal5 />} />
      <Route path="/proposal/6/*" element={<Proposal6 />} />
      <Route path="/proposal/7/*" element={<Proposal7 />} />
      <Route path="/proposal/8/*" element={<Proposal8 />} />
      <Route path="/proposal/9/*" element={<Proposal9 />} />
      <Route path="/proposal/10/*" element={<Proposal10 />} />
    </Routes>
  );
}
