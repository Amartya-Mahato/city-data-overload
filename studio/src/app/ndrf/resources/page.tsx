
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
import { List } from "lucide-react";

const resources = [
  { id: "RES-01", type: "Inflatable Boat", quantity: 20, location: "Central Warehouse", status: "Available" },
  { id: "RES-02", type: "First-Aid Kits", quantity: 150, location: "Deployed (South Zone)", status: "In Use" },
  { id: "RES-03", type: "Tents", quantity: 50, location: "Central Warehouse", status: "Available" },
  { id: "RES-04", type: "Satellite Phones", quantity: 30, location: "North Zone Hub", status: "Standby" },
];

export default function ResourceInventoryPage() {
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Resource Inventory</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><List /> NDRF Resource Inventory</CardTitle>
          <CardDescription>A live inventory of all available and deployed resources.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Resource ID</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Quantity</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {resources.map((resource) => (
                <TableRow key={resource.id}>
                  <TableCell className="font-medium">{resource.id}</TableCell>
                  <TableCell>{resource.type}</TableCell>
                  <TableCell>{resource.quantity}</TableCell>
                  <TableCell>{resource.location}</TableCell>
                   <TableCell>
                    <Badge variant={resource.status === 'Available' ? 'secondary' : resource.status === 'In Use' ? 'destructive' : 'default'}>
                      {resource.status}
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
