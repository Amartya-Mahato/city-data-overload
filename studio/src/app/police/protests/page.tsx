
"use client";

import * as React from "react";
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
import { Button } from "@/components/ui/button";
import { Megaphone, MapPin, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/hooks/use-toast";

const protests = [
  { location: "Freedom Park", status: "Ongoing", crowdSize: "Approx. 500", severity: "High" },
  { location: "Town Hall", status: "Forming", crowdSize: "Approx. 50", severity: "Medium" },
  { location: "Majestic", status: "Dispersed", crowdSize: "N/A", severity: "Low" },
];

export default function ActiveProtestsPage() {
  const { toast } = useToast();

  const handleAction = (action: string, location: string) => {
    toast({
      title: "Action Initiated",
      description: `${action} for protest at ${location}.`,
    });
  };

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Active Protests</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Megaphone /> Live Protest Tracker</CardTitle>
          <CardDescription>Real-time updates on active and forming protests across the city.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Location</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Crowd Size</TableHead>
                <TableHead>Severity</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {protests.map((protest) => (
                <TableRow key={protest.location}>
                  <TableCell className="font-medium flex items-center gap-2"><MapPin size={16} /> {protest.location}</TableCell>
                  <TableCell>
                     <Badge variant={protest.status === 'Ongoing' ? 'destructive' : protest.status === 'Forming' ? 'default' : 'secondary'}>
                        {protest.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{protest.crowdSize}</TableCell>
                  <TableCell>
                    <Badge variant={protest.severity === 'High' ? 'destructive' : 'default'}>
                        {protest.severity}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="h-8 w-8 p-0">
                          <span className="sr-only">Open menu</span>
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleAction("Viewing on Map", protest.location)}>View on Map</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleAction("Unit Dispatched", protest.location)}>Dispatch Unit</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleAction("Zone Flagged as Unsafe", protest.location)}>Flag Unsafe Zone</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
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
