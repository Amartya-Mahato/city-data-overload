
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

const units = [
  { id: "E-01", type: "Engine", status: "Available", station: "Station 1 (HSR Layout)" },
  { id: "L-01", type: "Ladder", status: "Deployed", station: "Station 2 (Koramangala)" },
  { id: "E-02", type: "Engine", status: "Available", station: "Station 1 (HSR Layout)" },
  { id: "R-01", type: "Rescue", status: "Maintenance", station: "Central Hub" },
];

export default function UnitStatusPage() {
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Unit Status</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Truck /> Fire Department Unit Status</CardTitle>
          <CardDescription>Live status of all fire department units.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Unit ID</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Current Station</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {units.map((unit) => (
                <TableRow key={unit.id}>
                  <TableCell className="font-medium">{unit.id}</TableCell>
                  <TableCell>{unit.type}</TableCell>
                  <TableCell>
                    <Badge variant={unit.status === 'Available' ? 'secondary' : unit.status === 'Deployed' ? 'destructive' : 'default'}>
                      {unit.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{unit.station}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
