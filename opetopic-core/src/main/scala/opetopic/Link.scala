/**
  * Link.scala - Calculating the link
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic

import mtl._

object Link {

  def link[A](c: SComplex[A], fa: FaceAddr): Option[SCardinal[A]] = {

    val codim = fa.codim
    val addr = fa.address


    c.grab(fa.codim) match {
      case (fcmplx, nst :: ns) => {
        for {
          bd <- SCmplxZipper(fcmplx).focusDeriv[Boolean]
          bn = fcmplx.head.mapWithAddr({
            case (_, baddr) => baddr == addr
          })
          // _ = println("Boolean coface nesting: " + bn.toString)
          cofaces <- nst.bondTraverse(bn, bd)({
            case (a, srcBs, tgtB) => {
              Some((a, tgtB || srcBs.toList.exists(b => b)))
            }
          })

          // Lame.  Think of a better setup
          addrCofaces = cofaces.mapWithAddr({
            case ((a, b), addr) => (a, addr, b)
          })

          mp <- (fcmplx >> nst).bondingMap

          trpl <- addr match {
            case Nil => Some((SNstZipper(addrCofaces), true, None))
            case d :: ds => {
              addrCofaces.seek(mp(ds)).map(z => {
                val dataOpt =
                  z.parent match {
                    case None => None
                    case Some(p) => Some(LinkData(p.focus.baseValue._1, p.focus.baseValue._2))
                  }

                (z, false, dataOpt)
              })
            }
          }

          (zp, asc, lastOpt) = trpl

          _ = println("Starting link calculation at: " + zp.focus.baseValue._1.toString)

          startCard = SCardinal(||(SDot(LinkData(zp.focus.baseValue._1, zp.focus.baseValue._2))))

          nxtMpOpt = ns match {
            case Nil => None
            case nn :: _ => {
              for {
                m <- (fcmplx >> nst >> nn).bondingMap
                blorp = nn.mapWithAddr({ case (a, addr) => LinkData(a, addr) })
                kvs <- m.toList.traverse({
                  case (k, v) => blorp.elementAt(v).map(r => (k, r))
                })
              } yield Map(kvs : _*)
            }
          }

          endCard <- buildLinkArrows(startCard, zp, asc, nxtMpOpt, lastOpt) match {
            case None => { println("There was an error") ; None }
            case Some(c) => { println("Trace complete") ; Some(c) }
          }

        } yield endCard.map(_.a)
      }
      case _ => None
    }

  }


  case class LinkData[A](val a: A, val addr: SAddr)

  def buildLinkArrows[A](
    c: SCardinal[LinkData[A]],
    z: SNstZipper[(A, SAddr, Boolean)],
    ascending: Boolean,
    mp: Option[Map[SAddr, LinkData[A]]],
    last: Option[LinkData[A]]
  ): Option[SCardinal[LinkData[A]]] = {

    println("Last is: " + last.toString)

    def arrowData(d: LinkData[A]): LinkData[A] =
      mp match {
        case None => d
        case Some(m) => m.get(d.addr).getOrElse(d)
      }

    if (ascending) {
      z.focus match {
        case SDot((a, addr, _)) => {
          println("Finished at: " + a.toString)
          val data = LinkData(a, addr)
          last match {
            case None => c.extrudeObject(data, arrowData(data))
            case Some(l) => c.extrudeObject(data, arrowData(l))
          }
        }
        case SBox((a, addr, _), SLeaf) => {
          println("Crossing box: " + a.toString)
          val data = LinkData(a, addr)
          val ad = arrowData(data)

          last match {
            case None => c.extrudeObject(data, ad).flatMap(cc =>
              cc.extrudeObject(data, ad)
            ).flatMap(ccc => buildLinkArrows(ccc, z, false, mp, last))
            case Some(l) => c.extrudeObject(data, arrowData(l)).flatMap(cc =>
              cc.extrudeObject(data, ad)
            ).flatMap(ccc => buildLinkArrows(ccc, z, false, mp, last))
          }
        }
        case SBox((a, addr, b), SNode(n, vs)) => {
          println("Entering box: " + a.toString)
          val data = LinkData(a, addr)
          val next = SNstZipper(n, SNstCtxt(((a, addr, b), SDeriv(vs)) :: z.ctxt.g))

          last match {
            case None => {
              println("No last, skipping extrusion")
              buildLinkArrows(c, next, true, mp, Some(data))
            }
            case Some(l) => 
              c.extrudeObject(data, arrowData(l)).flatMap(cc =>
                buildLinkArrows(cc, next, true, mp, Some(data))
              )
          }
        }
      }
    } else {
      z.ctxt.g match {
        case ((a, addr, b), SDeriv(verts, _)) :: cs => {

          verts.mapWithAddr({
            case (SLeaf, addr) => None
            case (SNode(n, sh), addr) =>
              if (n.baseValue._3) Some(addr) else None
          }).toList.filter(_.isDefined) match {
            case Nil => {
              // In this case, there is no outgoing guy and
              // we are going to move on to the next.

              if (b) {
                println("Descending past " + a.toString)
                val data = LinkData(a, addr)
                c.extrudeObject(data, arrowData(data)).flatMap(cc => 
                  z.parent.flatMap(p => buildLinkArrows(cc, p, false, mp, Some(data)))
                )
              } else {
                println("Error: parent is not a coface in descent")
                None
              }

            }
            case dOpt :: _ => {
              val newLast = z.parent match {
                case None => last
                case Some(p) => Some(LinkData(p.focus.baseValue._1, p.focus.baseValue._2))
              }

              z.sibling(SDir(dOpt.get)).flatMap(s =>
                buildLinkArrows(c, s, true, mp, newLast)
              )
            }
          }

        }
        case Nil => {
          println("Exiting nesting at " + z.focus.baseValue.toString)
          Some(c)
        }
      }
    }
  }

  // Uh, yeah, there are a couple of very serious problems.
  //
  //  1) Order is not preserved.  So you cannot count on the
  //     root element in some canopy to carry the correct initial
  //     address for the extrusion.  Well, actually, it probably
  //     is preserved in dimensions greater than 2.  Anyway, it's
  //     not totally clear how to handle this.
  //
  //  2) Worse: because of the peculiarities of how loops work,
  //     I don't think you can put them off until the dimension
  //     they actually occur, at which point it will be too late
  //     beacuse they will be covered by lower cells.  This means
  //     that the loops structure of the next dimension needs to
  //     be worked out at the same time as the current ongoing
  //     extrusion.  So they must be located and tagged ahead of time.
  //
  //  Right.  Work for another day.....    

  def doLinkExtrusion[A](
    card: SCardinal[LinkData[A]],
    nst: SNesting[(A, SAddr, Boolean)],
    mp: Map[SAddr, SCardAddr]
  ): Option[SCardinal[LinkData[A]]] = {

    type Data = (A, SAddr, Boolean)

    // Okay, so I think the structure has to be
    // similar to the fixLeaves routine in SComplex
    // since you have to return from a dot with a
    // full look at the outgoing canopy.

    // But it's slightly simpler in this case, because
    // you'll just be able to zip the two guys together
    // and move on.  No messing with this map stuff.
    // def vertical(n: SNesting[A], mk: LeafMarker[A]): Option[(SNesting[A], STree[LeafMarker[A]])] =

    def vertical(n: SNesting[Data]): Option[STree[SCardAddr]] = 
      n match {
        case SDot((a, addr, isCoface)) => {

          // Right, so in the dot case, things should already be taken care of.
          // The main thing here is to get started passing along the address
          // where we will be doing loop extrusions if we need to.


          None

        }
        case SBox((a, addr, isCoface), cn) => {


          for {
            hres <- horizontal(cn, ???)
          } yield ???

          // We want to extrude at the base address.  What does this mean?



        }
      }

    def horizontal(t: STree[SNesting[Data]], loopAddr: SCardAddr): Option[STree[SCardAddr]] =
      t match {
        case SLeaf => None
        case SNode(n, sh) => {

          for {
            vres <- vertical(n)
            hres <- vres.matchTraverse(sh)({
              case (la, br) => horizontal(br, la)
            })
          } yield ???


        }
      }

    None

  }


  // This could be made much more generic and reused but I'm not in
  // the mood to fight the types, so I'm just going to make the specific
  // case of building the associated cardinal.
  // def traceLink[A](z: SNstZipper[A], ascending: Boolean)(f: A => Boolean): Option[SNstZipper[A]] =
  //   if (ascending) {
  //     z.focus match {
  //       case SDot(a) => {
  //         println("Finished at: " + a.toString)
  //         Some(z)
  //       }
  //       case SBox(a, SLeaf) => {
  //         println("Crossing box: " + a.toString)
  //         traceLink(z, false)(f)
  //       }
  //       case SBox(a, SNode(n, vs)) => {
  //         println("Entering box: " + a.toString)
  //         traceLink(SNstZipper(n, SNstCtxt((a, SDeriv(vs)) :: z.ctxt.g)), true)(f)
  //       }
  //     }
  //   } else {
  //     z.ctxt.g match {
  //       case (a, SDeriv(verts, _)) :: cs => {

  //         verts.mapWithAddr({
  //           case (SLeaf, addr) => None
  //           case (SNode(n, sh), addr) =>
  //             if (f(n.baseValue)) Some(addr) else None
  //         }).toList.filter(_.isDefined) match {
  //           case Nil => {
  //             // In this case, there is no outgoing guy and
  //             // we are going to move on to the next.

  //             if (f(a)) {
  //               println("Descending past " + a.toString)
  //               z.parent.flatMap(p => traceLink(p, false)(f))
  //             } else {
  //               println("Error: parent is not a coface in descent")
  //               None
  //             }

  //           }
  //           case dOpt :: _ => {
  //             z.sibling(SDir(dOpt.get)).flatMap(s => traceLink(s, true)(f))
  //           }
  //         }

  //       }
  //       case Nil => {
  //         println("Exiting nesting at " + z.focus.baseValue.toString)
  //         Some(z)
  //       }
  //     }
  //   }


}
