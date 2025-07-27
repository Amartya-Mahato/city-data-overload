
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
import { Truck } from "lucide-react";

const activations = [
  { id: "ER-ACT-001", incidentId: "HOS-A-001", traumaLevel: "Severe", activationTime: "10:32 AM", status: "Active" },
  { id: "ER-ACT-002", incidentId: "HOS-A-002", traumaLevel: "Moderate", activationTime: "10:45 AM", status: "Pre-alert" },
  { id: "ER-ACT-003", incidentId: "PREV-987", traumaLevel: "Severe", activationTime: "Yesterday", status: "Closed" },
];

export default function ERActivationsPage() {
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">ER Activations</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Truck /> Emergency Room Activation Log</CardTitle>
          <CardDescription>A log of all ER "Ready" Protocol activations.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Activation ID</TableHead>
                <TableHead>Incident ID</TableHead>
                <TableHead>Trauma Level</TableHead>
                <TableHead>Activation Time</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {activations.map((activation) => (
                <TableRow key={activation.id}>
                  <TableCell className="font-medium">{activation.id}</TableCell>
                  <TableCell>{activation.incidentId}</TableCell>
                   <TableCell>
                    <Badge variant={activation.traumaLevel === 'Severe' ? 'destructive' : 'default'}>
                      {activation.traumaLevel}
                    </Badge>
                  </TableCell>
                  <TableCell>{activation.activationTime}</TableCell>
                  <TableCell>
                    <Badge variant={activation.status === 'Active' ? 'destructive' : activation.status === 'Pre-alert' ? 'default' : 'secondary'}>
                      {activation.status}
                    </Badge>
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
