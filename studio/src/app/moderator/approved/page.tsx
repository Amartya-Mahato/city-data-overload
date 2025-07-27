
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { CheckCircle2 } from "lucide-react";

const approved = [
  { id: "C-125", type: "Image", snippet: "Pothole on Sarjapur Road...", date: "2024-07-28", routedTo: "BMC" },
  { id: "C-124", type: "Text", snippet: "Lost dog in HSR Layout...", date: "2024-07-28", routedTo: "Community" },
  { id: "C-123", type: "Image", snippet: "Firecracker shop safety concern", date: "2024-07-27", routedTo: "Fire Dept." },
];

export default function ApprovedContentPage() {
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Approved Content</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><CheckCircle2 /> Approved & Routed Content</CardTitle>
          <CardDescription>A log of all content that has been approved and routed.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Content ID</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Snippet</TableHead>
                <TableHead>Approval Date</TableHead>
                <TableHead>Routed To</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {approved.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-medium">{item.id}</TableCell>
                  <TableCell>{item.type}</TableCell>
                  <TableCell className="truncate max-w-xs">{item.snippet}</TableCell>
                  <TableCell>{item.date}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{item.routedTo}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
