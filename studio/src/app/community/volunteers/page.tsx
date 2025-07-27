
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
import { Users, MoreHorizontal } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";

type Volunteer = {
  id: string;
  name: string;
  activity: string;
  status: "Active" | "Inactive";
  joined: string;
  email: string;
  phone: string;
};

const volunteers: Volunteer[] = [
  { id: "VOL-001", name: "Ananya Sharma", activity: "Cleanup Drive", status: "Active", joined: "2024-05-10", email: "ananya.s@example.com", phone: "987-654-3210" },
  { id: "VOL-002", name: "Rohan Verma", activity: "Pet Adoption Fair", status: "Active", joined: "2024-06-15", email: "rohan.v@example.com", phone: "876-543-2109" },
  { id: "VOL-003", name: "Priya Singh", activity: "N/A", status: "Inactive", joined: "2023-11-20", email: "priya.s@example.com", phone: "765-432-1098" },
];

export default function VolunteersPage() {
  const [selectedVolunteer, setSelectedVolunteer] = React.useState<Volunteer | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);

  const handleViewProfile = (volunteer: Volunteer) => {
    setSelectedVolunteer(volunteer);
    setIsDetailsOpen(true);
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Community Volunteers</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Users /> Volunteer List</CardTitle>
            <CardDescription>A list of all registered community volunteers.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Volunteer</TableHead>
                  <TableHead>Primary Activity</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date Joined</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {volunteers.map((volunteer) => (
                  <TableRow key={volunteer.id}>
                    <TableCell className="font-medium flex items-center gap-2">
                       <Avatar className="h-8 w-8">
                          <AvatarImage src={`https://placehold.co/100x100.png`} data-ai-hint="person face" />
                          <AvatarFallback>{volunteer.name.charAt(0)}</AvatarFallback>
                      </Avatar>
                      {volunteer.name}
                    </TableCell>
                    <TableCell>{volunteer.activity}</TableCell>
                     <TableCell>
                      <Badge variant={volunteer.status === 'Active' ? 'default' : 'secondary'}>
                        {volunteer.status}
                      </Badge>
                    </TableCell>
                    <TableCell>{volunteer.joined}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" className="h-8 w-8 p-0">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleViewProfile(volunteer)}>View Profile</DropdownMenuItem>
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

      <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Volunteer Profile: {selectedVolunteer?.name}</DialogTitle>
            <DialogDescription>
              Detailed information for this volunteer.
            </DialogDescription>
          </DialogHeader>
          {selectedVolunteer && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Volunteer ID</h4>
                <p>{selectedVolunteer.id}</p>
              </div>
               <div>
                <h4 className="font-semibold">Email</h4>
                <p>{selectedVolunteer.email}</p>
              </div>
               <div>
                <h4 className="font-semibold">Phone</h4>
                <p>{selectedVolunteer.phone}</p>
              </div>
              <div>
                <h4 className="font-semibold">Primary Activity</h4>
                <p className="text-muted-foreground">{selectedVolunteer.activity}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
