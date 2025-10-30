package com.certificadosapi.certificados.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.service.FiltrobusquedaService;

@RestController
@RequestMapping("/filtros")
public class FiltrobusquedaController {

    private final FiltrobusquedaService filtrobusquedaService;

    @Autowired
    public FiltrobusquedaController(FiltrobusquedaService filtrobusquedaService) {
        this.filtrobusquedaService = filtrobusquedaService;
    }


    //ENDPOINT PARA BUSCAR FACTURAS
    @GetMapping("/facturas")
    public ResponseEntity<List<Map<String, Object>>> buscarFacturas(
        @RequestParam(required = false) 
        @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaDesde,
        @RequestParam(required = false) 
        @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaHasta,
        @RequestParam(required = false) String idTercero,
        @RequestParam(required = false) String noContrato,
        @RequestParam(required = false) String nFact,
        @RequestParam(required = false) Integer cuentaCobro) {

        
        System.out.printf("FechaDesde: %s, FechaHasta: %s, IdTercero: %s, NoContrato: %s, NFact: %s, NCuentaCobro: %s%n",
            fechaDesde, fechaHasta, idTercero, noContrato, nFact, cuentaCobro);

        List<Map<String, Object>> resultados = filtrobusquedaService.buscarFacturas(
            fechaDesde, fechaHasta, idTercero, noContrato, nFact, cuentaCobro
        );
        return ResponseEntity.ok(resultados);
    }

    //ENDPOINT PARA OBTENER TERCEROS
    @GetMapping("/terceros")
    public ResponseEntity<List<Map<String, Object>>> obtenerTerceros() {
        List<Map<String, Object>> resultados = filtrobusquedaService.obtenerTerceros();
        return ResponseEntity.ok(resultados);
    }

    //ENDPOINT PARA OBTENER CONTRATOS
    @GetMapping("/contratos")
    public ResponseEntity<List<Map<String, Object>>> obtenerContratos(
            @RequestParam String idTerceroKey) {
        
        List<Map<String, Object>> resultados = filtrobusquedaService.obtenerContratos(idTerceroKey);
        return ResponseEntity.ok(resultados);
    }


    // Buscador para el filtro de atenciones (CUADRO VERDE)
    @GetMapping("/atenciones")
    public ResponseEntity<List<Map<String, Object>>> buscarAtenciones(
            @RequestParam(required = false) Long IdAtencion,
            @RequestParam(required = false) String HistClinica,
            @RequestParam(required = false) Integer Cliente,
            @RequestParam(required = false) String NoContrato,
            @RequestParam(required = false) Integer IdAreaAtencion,
            @RequestParam(required = false) Integer IdUnidadAtencion,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaDesde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaHasta,
            @RequestParam(required = false) String nFact,
            @RequestParam(required = false) Integer nCuentaCobro,
            @RequestParam(required = false) Boolean soloFacturados
    ) {
        System.out.printf(
            "Filtros recibidos: IdAtencion=%s, HistClinica=%s, Cliente=%s, NoContrato=%s, " +
            "IdAreaAtencion=%s, IdUnidadAtencion=%s, FechaDesde=%s, FechaHasta=%s, nFact=%s, nCuentaCobro=%s, soloFacturados=$s",
            IdAtencion, HistClinica, Cliente, NoContrato, IdAreaAtencion, IdUnidadAtencion, FechaDesde, FechaHasta, nFact, nCuentaCobro, soloFacturados
        );

        List<Map<String, Object>> data = filtrobusquedaService.buscarAtenciones(
                IdAtencion, HistClinica, Cliente, NoContrato,
                IdAreaAtencion, IdUnidadAtencion, FechaDesde, FechaHasta, 
                nFact, nCuentaCobro, soloFacturados);

        return ResponseEntity.ok(data);
    }
}